package com.example.clandestinely.route.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One walkable path segment between two nodes. It can be walked in both directions.
 * The Boolean/Double fields can be null, which means "not known yet"
 * (see null_handling in campus_graph.json).
 */
public final class Edge {

    private final String id;
    private final String from;
    private final String to;
    private final double distanceM;
    private final Boolean covered;
    private final Double shadeScore;
    private final Boolean floodProne;
    private final Boolean trafficPeak;
    private final Boolean stepFree;
    private final Map<Integer, Double> shadeByHour;
    private final List<LatLon> geometry;

    public Edge(String id, String from, String to, double distanceM,
                Boolean covered, Double shadeScore, Boolean floodProne, Boolean trafficPeak, Boolean stepFree,
                Map<Integer, Double> shadeByHour, List<LatLon> geometry) {
        this.id = id;
        this.from = from;
        this.to = to;
        this.distanceM = distanceM;
        this.covered = covered;
        this.shadeScore = shadeScore;
        this.floodProne = floodProne;
        this.trafficPeak = trafficPeak;
        this.stepFree = stepFree;
        this.shadeByHour = Collections.unmodifiableMap(shadeByHour);
        this.geometry = Collections.unmodifiableList(geometry);
    }

    /** e.g. "FSKTM-J101" (see id_rules in campus_graph.json). */
    public String getId() { return id; }
    public String getFrom() { return from; }
    public String getTo() { return to; }
    public double getDistanceM() { return distanceM; }

    /** true = roofed, false = open, null = unknown. */
    public Boolean getCovered() { return covered; }
    /** 0 = full sun, 1 = full shade, null = unknown. */
    public Double getShadeScore() { return shadeScore; }
    public Boolean getFloodProne() { return floodProne; }
    public Boolean getTrafficPeak() { return trafficPeak; }
    /** false = has stairs, null = unknown. */
    public Boolean getStepFree() { return stepFree; }

    /** Shade per hour of the day (7 -> 0.9 means 90% shaded at 7am). Empty if not computed yet. */
    public Map<Integer, Double> getShadeByHour() { return shadeByHour; }

    /** The path's real shape. Empty means "draw a straight line between the two nodes". */
    public List<LatLon> getGeometry() { return geometry; }
}
