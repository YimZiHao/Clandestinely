package com.example.clandestinely.route.engine;

import com.example.clandestinely.route.model.Edge;

/**
 * How "expensive" it is to walk one path segment. The RouteFinder always picks the route
 * with the lowest total cost, so changing the cost changes which route wins.
 *
 * Week 6 only needs DISTANCE (Fastest mode). Week 7's Shadiest and Driest modes will be
 * other EdgeCost rules (e.g. "an open-air path counts as longer when it rains").
 */
public interface EdgeCost {

    /** The cost of walking this edge. Must be 0 or more. */
    double of(Edge edge);

    /** Fastest mode: the cost is simply the length in metres. */
    EdgeCost DISTANCE = Edge::getDistanceM;
}
