package com.example.clandestinely.route.model;

import java.util.Collections;
import java.util.List;

/**
 * One walking route found by the RouteFinder: the places/junctions it passes through,
 * in order, and the path segments (edges) walked between them.
 */
public final class Route {

    /**
     * Average walking speed used for the time estimate: 80 m per minute (about 4.8 km/h).
     * This is an assumption the team can change.
     */
    public static final double WALKING_METRES_PER_MINUTE = 80.0;

    private final String fromId;
    private final String toId;
    private final List<String> nodeIds;
    private final List<Edge> edges;
    private final double distanceM;

    public Route(String fromId, String toId, List<String> nodeIds, List<Edge> edges) {
        this.fromId = fromId;
        this.toId = toId;
        this.nodeIds = Collections.unmodifiableList(nodeIds);
        this.edges = Collections.unmodifiableList(edges);
        double total = 0;
        for (Edge edge : edges) {
            total += edge.getDistanceM();
        }
        this.distanceM = total;
    }

    public String getFromId() { return fromId; }
    public String getToId() { return toId; }

    /** Every node on the way, from start to destination (start and destination included). */
    public List<String> getNodeIds() { return nodeIds; }

    /** Every path segment walked, in order. Empty if start and destination are the same. */
    public List<Edge> getEdges() { return edges; }

    /** Total walking distance in metres. */
    public double getDistanceM() { return distanceM; }

    /** Estimated walking time in whole minutes (at least 1 for any real walk). */
    public int getWalkingMinutes() {
        if (edges.isEmpty()) {
            return 0;
        }
        return (int) Math.max(1, Math.round(distanceM / WALKING_METRES_PER_MINUTE));
    }
}
