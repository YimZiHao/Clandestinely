package com.example.clandestinely.route.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The whole campus map: every node and every path, as loaded from campus_graph.json. */
public final class CampusGraph {

    private final String version;
    private final List<Node> nodes;
    private final List<Edge> edges;
    private final List<Node> places;
    private final Map<String, Node> nodesById = new HashMap<>();

    public CampusGraph(String version, List<Node> nodes, List<Edge> edges) {
        this.version = version;
        this.nodes = Collections.unmodifiableList(nodes);
        this.edges = Collections.unmodifiableList(edges);
        List<Node> named = new ArrayList<>();
        for (Node node : nodes) {
            nodesById.put(node.getId(), node);
            if (!node.isJunction()) {
                named.add(node);
            }
        }
        this.places = Collections.unmodifiableList(named);
    }

    public String getVersion() { return version; }
    public List<Node> getNodes() { return nodes; }
    public List<Edge> getEdges() { return edges; }

    /** Named places only (no junctions). These are what the user can pick and see as markers. */
    public List<Node> getPlaces() { return places; }

    /** The node with this ID, or null if there isn't one. */
    public Node getNode(String id) { return nodesById.get(id); }
}
