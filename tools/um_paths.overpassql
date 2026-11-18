// CuacaUM — M4: walkable paths and roads on the UM campus.
// Paste this into https://overpass-turbo.eu, click Run, then Export -> "raw OSM data" (JSON)
// and save the file as data/osm_um_paths.json.
// Bounding box = all campus_graph.json nodes plus a margin (south, west, north, east).
[out:json][timeout:60];
(
  way["highway"~"^(footway|path|pedestrian|steps|corridor|living_street|service|residential|unclassified|tertiary|secondary|primary|cycleway|track)$"]
    (3.110,101.645,3.137,101.666);
);
out geom;
