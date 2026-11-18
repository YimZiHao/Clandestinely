package com.example.clandestinely.route.model;

/** A latitude/longitude pair (one point along a path). */
public final class LatLon {

    private final double lat;
    private final double lon;

    public LatLon(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public double getLat() { return lat; }
    public double getLon() { return lon; }
}
