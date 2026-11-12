#!/usr/bin/env python3
"""
CuacaUM — M4: health check for data/campus_graph.json.

Usage (from the repo root):
    python3 tools/check_graph.py data/campus_graph.json

Checks, in plain words:
  - every node and edge has a unique id
  - every edge starts and ends at a node that exists
  - values are in range (shade 0-1, distance > 0, true/false fields really true/false/null)
  - every named place can reach every other named place
  - IDs follow the id_rules (places ALL CAPS, junctions J001..., paths START-END)
  - how many values are still unknown (null), so you know what is left to fill in
Exits with code 1 if there is a real error, so it can run before every commit.
"""
import json
import re
import sys
from collections import defaultdict

BOOL_FIELDS = ("covered", "flood_prone", "traffic_peak", "step_free")

# The ID rules from campus_graph.json -> id_rules
PLACE_ID = re.compile(r"^[A-Z][A-Z0-9]{1,7}$")
JUNCTION_ID = re.compile(r"^J[0-9]{3,}$")
EDGE_SUFFIX = re.compile(r"^-([2-9]|[1-9][0-9]+)$")


def main(path):
    with open(path, encoding="utf-8") as f:
        doc = json.load(f)
    nodes, edges = doc.get("nodes", []), doc.get("edges", [])
    errors, warnings = [], []

    ids = [n["id"] for n in nodes]
    node_ids = set(ids)
    for dup in sorted({i for i in ids if ids.count(i) > 1}):
        errors.append(f"node id used twice: {dup}")
    eids = [e.get("id") for e in edges]
    for dup in sorted({i for i in eids if eids.count(i) > 1}):
        errors.append(f"edge id used twice: {dup}")

    for n in nodes:
        nid = n["id"]
        if n.get("group") == "junction":
            if not JUNCTION_ID.match(nid):
                errors.append(f"junction id {nid} should look like J001")
        elif not PLACE_ID.match(nid) or JUNCTION_ID.match(nid):
            errors.append(f"place id {nid} should be 2-8 ALL CAPS letters/digits with no '-' (and not look like a junction)")

    adj = defaultdict(set)
    for e in edges:
        eid = e.get("id", "?")
        if not e.get("id"):
            errors.append(f"edge {e.get('from')}->{e.get('to')} has no id")
        for end in ("from", "to"):
            if e.get(end) not in node_ids:
                errors.append(f"{eid}: '{end}' node {e.get(end)} does not exist")
        if e.get("from") == e.get("to"):
            errors.append(f"{eid}: starts and ends at the same node")
        a, b = str(e.get("from")), str(e.get("to"))
        base = f"{a}-{b}"
        if a > b:
            errors.append(f"{eid}: 'from' and 'to' should be in alphabetical order ({b} before {a})")
        elif e.get("id") and e["id"] != base and not (e["id"].startswith(base) and EDGE_SUFFIX.match(e["id"][len(base):])):
            errors.append(f"{eid}: id should be {base} (or {base}-2, -3 ... for a second path)")
        d = e.get("distance_m")
        if not isinstance(d, (int, float)) or d <= 0:
            errors.append(f"{eid}: distance_m must be a number above 0 (got {d})")
        s = e.get("shade_score")
        if s is not None and not (isinstance(s, (int, float)) and 0 <= s <= 1):
            errors.append(f"{eid}: shade_score must be 0-1 or null (got {s})")
        for hour, v in (e.get("shade_by_hour") or {}).items():
            if not (hour.isdigit() and 0 <= int(hour) <= 23 and isinstance(v, (int, float)) and 0 <= v <= 1):
                errors.append(f"{eid}: bad shade_by_hour entry {hour}: {v}")
        for k in BOOL_FIELDS:
            if e.get(k) not in (True, False, None):
                errors.append(f"{eid}: {k} must be true, false or null (got {e.get(k)!r})")
        adj[e.get("from")].add(e.get("to"))
        adj[e.get("to")].add(e.get("from"))

    places = [n["id"] for n in nodes if n.get("group") != "junction"]
    if edges and places:
        seen, stack = set(), [places[0]]
        while stack:
            x = stack.pop()
            if x not in seen:
                seen.add(x)
                stack.extend(adj[x])
        for p in places:
            if p not in seen:
                errors.append(f"place {p} cannot be reached from {places[0]}")
        for n in nodes:
            if n.get("group") == "junction" and not adj[n["id"]]:
                warnings.append(f"junction {n['id']} has no edges")
    elif not edges:
        warnings.append("no edges yet")

    for n in nodes:
        if n.get("group") != "junction" and not n.get("verified"):
            warnings.append(f"place {n['id']} is not verified yet")

    print(f"{path}: {len(places)} places, {len(nodes) - len(places)} junctions, {len(edges)} edges")
    if edges:
        for k in ("covered", "shade_score", "flood_prone", "traffic_peak", "step_free"):
            unknown = sum(e.get(k) is None for e in edges)
            print(f"  {k:13} unknown on {unknown}/{len(edges)} edges")
    for w in warnings:
        print("  warning:", w)
    for err in errors:
        print("  ERROR:", err)
    print("OK" if not errors else f"{len(errors)} error(s)")
    return 1 if errors else 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1]))
