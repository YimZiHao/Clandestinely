#!/usr/bin/env python3
"""
CuacaUM — M4: build the campus graph's edges from an OpenStreetMap export.

Usage (from the repo root):
    python3 tools/osm_to_graph.py data/osm_um_paths.json data/campus_graph.json

What it does, in plain words:
  1. Reads every walkable path and road in the OpenStreetMap export.
  2. Cuts them into pieces wherever two paths cross. Each piece becomes an edge.
  3. Links each campus place (FSKTM, LIB, ...) to the closest point on the path network.
  4. Throws away dead-end bits that don't lead to any campus place.
  5. Joins pieces that simply continue one another into one edge.
  6. Writes the junction nodes and edges back into campus_graph.json
     (your 15 named places are kept exactly as they are).

Only uses the Python standard library, so it runs on any Mac with python3.
"""
import argparse
import json
import math
import re
import sys
from collections import defaultdict

# Which OpenStreetMap "highway" types count as walkable.
WALKABLE = {
    "footway", "path", "pedestrian", "steps", "corridor", "living_street", "service",
    "residential", "unclassified", "tertiary", "secondary", "primary", "cycleway", "track",
}
# Walking along these means walking beside heavy traffic -> traffic_peak = true.
BUSY_ROADS = {"primary", "secondary", "tertiary"}
# A campus place further than this from any path gets a warning (its pin may be off).
CONNECTOR_WARN_M = 60

# ID rules (also written in campus_graph.json -> id_rules)
PLACE_ID = re.compile(r"^[A-Z][A-Z0-9]{1,7}$")
JUNCTION_ID = re.compile(r"^J[0-9]{3,}$")


# ---------- geometry helpers ----------

def haversine(a, b):
    """Distance in metres between two (lat, lon) points."""
    r = 6371000.0
    la1, lo1 = map(math.radians, a)
    la2, lo2 = map(math.radians, b)
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def line_length(points):
    return sum(haversine(points[i], points[i + 1]) for i in range(len(points) - 1))


# ---------- step 1: read the OSM export ----------

def is_walkable(tags):
    if tags.get("highway") not in WALKABLE or tags.get("area") == "yes":
        return False
    if tags.get("foot") in ("no", "private"):
        return False
    if tags.get("access") in ("no", "private") and tags.get("foot") not in ("yes", "designated", "permissive"):
        return False
    return True


def edge_attrs(tags):
    """What OpenStreetMap can tell us about one path. Unknown = None (null in JSON)."""
    hw = tags["highway"]
    covered = None
    if tags.get("covered") in ("yes", "arcade", "colonnade") or hw == "corridor" \
            or tags.get("tunnel") == "building_passage":
        covered = True
    elif tags.get("covered") == "no":
        covered = False
    # step_free: stairs are reliably mapped in OSM; anything else is unknown unless tagged.
    step_free = None
    if hw == "steps" or tags.get("wheelchair") == "no":
        step_free = False
    elif tags.get("wheelchair") in ("yes", "designated"):
        step_free = True
    return {"highway": hw, "covered": covered, "traffic_peak": hw in BUSY_ROADS, "step_free": step_free}


def load_ways(osm):
    coords = {}
    for el in osm.get("elements", []):
        if el.get("type") == "node":
            coords[el["id"]] = (el["lat"], el["lon"])
    ways = []
    for el in osm.get("elements", []):
        if el.get("type") != "way" or not is_walkable(el.get("tags", {})):
            continue
        nids = el.get("nodes", [])
        geom = el.get("geometry") or []
        if len(geom) == len(nids):
            for nid, g in zip(nids, geom):
                if g:
                    coords[nid] = (g["lat"], g["lon"])
        # Split the way wherever a node has no coordinates, so we never draw a fake straight line.
        run = []
        for nid in nids + [None]:
            if nid is not None and nid in coords:
                run.append(nid)
            else:
                if len(run) >= 2:
                    ways.append((el["id"], edge_attrs(el["tags"]), run))
                run = []
    return coords, ways


# ---------- the graph we build up ----------

class Graph:
    def __init__(self):
        self.seg = {}                      # seg id -> dict(u, v, nodes, attrs, ways)
        self.inc = defaultdict(set)        # vertex -> seg ids touching it
        self._next = 0

    def add(self, u, v, nodes, attrs, ways):
        sid = self._next
        self._next += 1
        self.seg[sid] = {"u": u, "v": v, "nodes": nodes, "attrs": attrs, "ways": list(ways)}
        self.inc[u].add(sid)
        self.inc[v].add(sid)
        return sid

    def remove(self, sid):
        s = self.seg.pop(sid)
        for x in (s["u"], s["v"]):
            self.inc[x].discard(sid)
            if not self.inc[x]:
                del self.inc[x]

    def degree(self, x):
        return sum(2 if self.seg[s]["u"] == self.seg[s]["v"] else 1 for s in self.inc.get(x, ()))


def build(coords, ways, places):
    # Step 2: a node becomes a junction if it is a way's end, or shared by 2+ ways.
    use = defaultdict(int)
    for _, _, nids in ways:
        for i, n in enumerate(nids):
            use[n] += 2 if i in (0, len(nids) - 1) else 1
    vertices = {n for n, c in use.items() if c >= 2}

    # Step 3: snap each campus place to the nearest point that lies on a path.
    on_network = {n for _, _, nids in ways for n in nids}
    snaps = {}
    for p in places:
        here = (p["lat"], p["lon"])
        best = min(on_network, key=lambda n: haversine(here, coords[n]))
        snaps[p["id"]] = (best, haversine(here, coords[best]))
        vertices.add(best)

    g = Graph()
    for wid, attrs, nids in ways:
        start = 0
        for i in range(1, len(nids)):
            if nids[i] in vertices or i == len(nids) - 1:
                piece = nids[start:i + 1]
                if piece[0] != piece[-1]:          # drop pieces that loop back to themselves
                    g.add(piece[0], piece[-1], piece, attrs, [wid])
                start = i

    # Each place gets its own vertex, joined to the network by a short "connector" edge.
    place_vertex = {}
    for p in places:
        osm_node, dist = snaps[p["id"]]
        pv = ("place", p["id"])
        coords[pv] = (p["lat"], p["lon"])
        g.add(pv, osm_node, [pv, osm_node],
              {"highway": "connector", "covered": None, "traffic_peak": False, "step_free": None}, [])
        place_vertex[p["id"]] = pv
    keep = set(place_vertex.values())

    # Step 4a: keep only the parts of the network that some campus place is connected to.
    seen, stack = set(), list(keep)
    while stack:
        x = stack.pop()
        if x in seen:
            continue
        seen.add(x)
        for s in g.inc.get(x, ()):
            seg = g.seg[s]
            stack.append(seg["v"] if seg["u"] == x else seg["u"])
    for sid in [s for s, seg in g.seg.items() if seg["u"] not in seen]:
        g.remove(sid)

    # Step 4b: repeatedly cut dead ends that don't end at a campus place.
    changed = True
    while changed:
        changed = False
        for x in list(g.inc):
            if x not in keep and x in g.inc and g.degree(x) == 1:
                g.remove(next(iter(g.inc[x])))
                changed = True

    # Step 5: join two pieces that meet at a plain bend (2 pieces, same attributes).
    changed = True
    while changed:
        changed = False
        for x in list(g.inc):
            if x in keep or x not in g.inc or len(g.inc[x]) != 2:
                continue
            a, b = (g.seg[s] for s in g.inc[x])
            if a["attrs"] != b["attrs"] or a["u"] == a["v"] or b["u"] == b["v"]:
                continue
            na = a["nodes"] if a["v"] == x else a["nodes"][::-1]     # ... -> x
            nb = b["nodes"] if b["u"] == x else b["nodes"][::-1]     # x -> ...
            if na[0] == nb[-1]:
                continue                                            # would make a loop
            ids = list(g.inc[x])
            for s in ids:
                g.remove(s)
            g.add(na[0], nb[-1], na + nb[1:], a["attrs"], a["ways"] + b["ways"])
            changed = True

    # Which places can reach the first place? (The others are on an island of paths.)
    first = next(iter(place_vertex.values()))
    seen, stack = set(), [first]
    while stack:
        x = stack.pop()
        if x in seen:
            continue
        seen.add(x)
        for s in g.inc.get(x, ()):
            seg = g.seg[s]
            stack.append(seg["v"] if seg["u"] == x else seg["u"])
    reachable = {pid for pid, pv in place_vertex.items() if pv in seen}
    return g, place_vertex, snaps, reachable


# ---------- step 6: write campus_graph.json ----------

def assign_junction_ids(junctions, coords, graph_doc):
    """Junction IDs are given once and never change or get reused (see id_rules).
    A junction found in the previous campus_graph.json keeps its old ID (matched by its
    OpenStreetMap node number); only brand-new junctions get new numbers."""
    old = {n["osm_node"]: n["id"] for n in graph_doc["nodes"]
           if n.get("group") == "junction" and "osm_node" in n}
    used = [int(i[1:]) for i in old.values() if i[1:].isdigit()]
    counters = graph_doc.setdefault("id_counters", {})
    next_number = max([counters.get("last_junction", 0)] + used) + 1
    ids = {}
    for x in sorted(junctions, key=lambda x: coords[x]):
        if x in old:
            ids[x] = old[x]
        else:
            ids[x] = f"J{next_number:03d}"
            next_number += 1
    counters["last_junction"] = next_number - 1
    return ids


def write_graph(g, coords, place_vertex, graph_doc):
    name_of = {pv: pid for pid, pv in place_vertex.items()}
    junctions = [x for x in g.inc if x not in name_of]
    name_of.update(assign_junction_ids(junctions, coords, graph_doc))

    places = [n for n in graph_doc["nodes"] if n.get("group") != "junction"]
    graph_doc["nodes"] = places + [
        {"id": name_of[x], "name": "Junction", "group": "junction",
         "lat": round(coords[x][0], 7), "lon": round(coords[x][1], 7), "verified": False,
         "osm_node": x}
        for x in sorted(junctions, key=lambda x: name_of[x])
    ]

    edges = []
    for seg in g.seg.values():
        u, v = name_of[seg["u"]], name_of[seg["v"]]
        pts = [coords[n] for n in seg["nodes"]]
        if u > v:                                   # store each edge in a stable direction
            u, v, pts = v, u, pts[::-1]
        edges.append({
            "from": u, "to": v,
            "distance_m": round(line_length(pts), 1),
            "covered": seg["attrs"]["covered"],
            "shade_score": None,
            "flood_prone": None,
            "traffic_peak": seg["attrs"]["traffic_peak"],
            "step_free": seg["attrs"]["step_free"],
            "source": {"highway": seg["attrs"]["highway"], "osm_ways": sorted(set(seg["ways"]))},
            "geometry": [[round(la, 7), round(lo, 7)] for la, lo in pts],
        })
    # Path ID = START-END in alphabetical order; a second path between the same two
    # points gets -2, a third -3, ... (shortest first).
    edges.sort(key=lambda e: (e["from"], e["to"], e["distance_m"]))
    seen = defaultdict(int)
    out = []
    for e in edges:
        base = f"{e['from']}-{e['to']}"
        seen[base] += 1
        out.append({"id": base if seen[base] == 1 else f"{base}-{seen[base]}", **e})
    graph_doc["edges"] = out
    return graph_doc


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("osm_json", help="Overpass export, e.g. data/osm_um_paths.json")
    ap.add_argument("graph_json", help="campus_graph.json with the named places")
    ap.add_argument("-o", "--out", help="where to write (default: overwrite graph_json)")
    args = ap.parse_args()

    with open(args.osm_json, encoding="utf-8") as f:
        osm = json.load(f)
    with open(args.graph_json, encoding="utf-8") as f:
        doc = json.load(f)

    coords, ways = load_ways(osm)
    if not ways:
        sys.exit("No walkable paths found in the OSM export. Did the Overpass query run on the right area?")
    places = [n for n in doc["nodes"] if n.get("group") != "junction"]
    bad = [p["id"] for p in places if not PLACE_ID.match(p["id"]) or JUNCTION_ID.match(p["id"])]
    if bad:
        sys.exit(f"These place IDs break the id_rules (ALL CAPS letters/digits, no '-'): {bad}")
    g, place_vertex, snaps, reachable = build(coords, ways, places)
    doc = write_graph(g, coords, place_vertex, doc)

    out = args.out or args.graph_json
    with open(out, "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=2, ensure_ascii=False)
        f.write("\n")

    # Report, so you can see what needs checking by hand.
    edges = doc["edges"]
    junctions = [n for n in doc["nodes"] if n["group"] == "junction"]
    print(f"Wrote {out}: {len(places)} places, {len(junctions)} junctions, {len(edges)} edges, "
          f"{sum(e['distance_m'] for e in edges) / 1000:.1f} km of paths")
    print(f"  covered=true from OSM tags: {sum(e['covered'] is True for e in edges)} edges "
          f"(the rest are unknown -> check on satellite/Street View)")
    print(f"  beside busy roads: {sum(e['traffic_peak'] for e in edges)} edges")
    print(f"  stairs (step_free=false): {sum(e['step_free'] is False for e in edges)} edges")
    for pid, (_, dist) in sorted(snaps.items(), key=lambda kv: -kv[1][1]):
        if dist > CONNECTOR_WARN_M:
            print(f"  WARNING {pid}: {dist:.0f} m from the nearest mapped path — check its pin or add the missing path")
    for pid in sorted(set(place_vertex) - reachable):
        print(f"  WARNING {pid}: not connected to the other places")


if __name__ == "__main__":
    main()
