package com.example.clandestinely.ui.route;

import android.graphics.Color;

import com.example.clandestinely.R;
import com.example.clandestinely.route.model.CampusGraph;
import com.example.clandestinely.route.model.Edge;
import com.example.clandestinely.route.model.LatLon;
import com.example.clandestinely.route.model.Node;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the campus graph on an osmdroid map: a line for every path and a pin for every place.
 * Roofed paths are blue, stairs are orange, everything else is grey.
 * If we ever change map library, this class and RouteFragment's map setup are all that change.
 */
public final class CampusMapRenderer {

    private static final int COLOR_PATH = Color.argb(160, 110, 110, 110);
    private static final int COLOR_COVERED = Color.argb(230, 30, 136, 229);
    private static final int COLOR_STAIRS = Color.argb(230, 245, 124, 0);

    private CampusMapRenderer() { }

    public static void draw(MapView map, CampusGraph graph) {
        map.getOverlays().clear();

        for (Edge edge : graph.getEdges()) {
            Polyline line = new Polyline(map);
            line.setPoints(pointsOf(edge, graph));
            line.getOutlinePaint().setColor(colorFor(edge));
            line.getOutlinePaint().setStrokeWidth(Boolean.TRUE.equals(edge.getCovered()) ? 9f : 5f);
            line.setTitle(edge.getId());
            map.getOverlays().add(line);
        }

        String notVerified = map.getContext().getString(R.string.route_not_verified);
        for (Node place : graph.getPlaces()) {
            Marker marker = new Marker(map);
            marker.setPosition(new GeoPoint(place.getLat(), place.getLon()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setTitle(place.getName());
            marker.setSnippet(place.isVerified() ? place.getId() : place.getId() + " · " + notVerified);
            marker.setAlpha(place.isVerified() ? 1f : 0.5f);   // faded = pin still needs checking
            map.getOverlays().add(marker);
        }

        // OpenStreetMap requires the "© OpenStreetMap contributors" credit on the map.
        map.getOverlays().add(new CopyrightOverlay(map.getContext()));
        map.invalidate();
    }

    private static int colorFor(Edge edge) {
        if (Boolean.FALSE.equals(edge.getStepFree())) {
            return COLOR_STAIRS;
        }
        if (Boolean.TRUE.equals(edge.getCovered())) {
            return COLOR_COVERED;
        }
        return COLOR_PATH;
    }

    /** The path's real shape, or a straight line between its two nodes if it has none. */
    private static List<GeoPoint> pointsOf(Edge edge, CampusGraph graph) {
        List<GeoPoint> points = new ArrayList<>();
        for (LatLon p : edge.getGeometry()) {
            points.add(new GeoPoint(p.getLat(), p.getLon()));
        }
        if (points.isEmpty()) {
            Node from = graph.getNode(edge.getFrom());
            Node to = graph.getNode(edge.getTo());
            if (from != null && to != null) {
                points.add(new GeoPoint(from.getLat(), from.getLon()));
                points.add(new GeoPoint(to.getLat(), to.getLon()));
            }
        }
        return points;
    }
}
