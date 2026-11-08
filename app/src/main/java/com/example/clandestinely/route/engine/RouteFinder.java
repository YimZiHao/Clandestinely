package com.example.clandestinely.route.engine;

import com.example.clandestinely.route.model.CampusGraph;
import com.example.clandestinely.route.model.Edge;
import com.example.clandestinely.route.model.Route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Finds the cheapest walking route between two nodes with Dijkstra's algorithm.
 *
 * How Dijkstra works, in plain words:
 *  1. Start at the start node with a total cost of 0.
 *  2. Always look next at the node with the lowest total cost found so far
 *     (that's what the PriorityQueue does).
 *  3. From that node, try every path leading out of it. If going that way reaches a
 *     neighbour more cheaply than any way found before, remember the new cost and
 *     which path we came by.
 *  4. Stop when the destination is the cheapest node left to look at. Its cost is then
 *     guaranteed to be the lowest possible.
 *  5. Walk backwards along the "came by" paths from the destination to the start
 *     to get the route.
 *
 * Pure Java (no Android code), so it can be tested on any computer.
 */
public final class RouteFinder {

    private final CampusGraph graph;
    /** For each node ID: every edge that touches it (edges can be walked both ways). */
    private final Map<String, List<Edge>> edgesAt = new HashMap<>();

    public RouteFinder(CampusGraph graph) {
        this.graph = graph;
        for (Edge edge : graph.getEdges()) {
            edgesAt.computeIfAbsent(edge.getFrom(), id -> new ArrayList<>()).add(edge);
            edgesAt.computeIfAbsent(edge.getTo(), id -> new ArrayList<>()).add(edge);
        }
    }

    /**
     * The cheapest route from one node to another, using the given cost rule.
     * Returns null if either ID doesn't exist or there is no way to walk between them.
     */
    public Route find(String fromId, String toId, EdgeCost cost) {
        if (graph.getNode(fromId) == null || graph.getNode(toId) == null) {
            return null;
        }
        if (fromId.equals(toId)) {
            return new Route(fromId, toId, Collections.singletonList(fromId), Collections.<Edge>emptyList());
        }

        Map<String, Double> bestCost = new HashMap<>();   // cheapest total cost found to each node
        Map<String, Edge> cameBy = new HashMap<>();       // the edge used to reach each node that cheaply
        PriorityQueue<Step> toVisit = new PriorityQueue<>();

        bestCost.put(fromId, 0.0);
        toVisit.add(new Step(fromId, 0.0));

        while (!toVisit.isEmpty()) {
            Step current = toVisit.poll();
            if (current.cost > bestCost.get(current.nodeId)) {
                continue;   // an old, more expensive entry for a node we already improved
            }
            if (current.nodeId.equals(toId)) {
                break;      // the destination is the cheapest left: its route can't get better
            }
            for (Edge edge : edgesAt.getOrDefault(current.nodeId, Collections.<Edge>emptyList())) {
                String next = otherEnd(edge, current.nodeId);
                double newCost = current.cost + cost.of(edge);
                Double known = bestCost.get(next);
                if (known == null || newCost < known) {
                    bestCost.put(next, newCost);
                    cameBy.put(next, edge);
                    toVisit.add(new Step(next, newCost));
                }
            }
        }

        if (!cameBy.containsKey(toId)) {
            return null;    // the destination was never reached
        }

        // Walk backwards from the destination to rebuild the route.
        LinkedList<Edge> edges = new LinkedList<>();
        LinkedList<String> nodeIds = new LinkedList<>();
        String at = toId;
        nodeIds.addFirst(at);
        while (!at.equals(fromId)) {
            Edge edge = cameBy.get(at);
            edges.addFirst(edge);
            at = otherEnd(edge, at);
            nodeIds.addFirst(at);
        }
        return new Route(fromId, toId, nodeIds, edges);
    }

    /** The node at the other end of an edge. */
    private static String otherEnd(Edge edge, String nodeId) {
        return edge.getFrom().equals(nodeId) ? edge.getTo() : edge.getFrom();
    }

    /** One entry in the "to visit" queue: a node and the total cost to reach it. */
    private static final class Step implements Comparable<Step> {
        final String nodeId;
        final double cost;

        Step(String nodeId, double cost) {
            this.nodeId = nodeId;
            this.cost = cost;
        }

        @Override
        public int compareTo(Step other) {
            return Double.compare(cost, other.cost);   // cheapest first
        }
    }
}
