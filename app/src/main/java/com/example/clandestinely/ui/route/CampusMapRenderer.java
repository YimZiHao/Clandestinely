package com.example.clandestinely.ui.route;

import android.graphics.Color;
import android.graphics.Paint;

import com.example.clandestinely.R;
import com.example.clandestinely.route.model.CampusGraph;
import com.example.clandestinely.route.model.Edge;
import com.example.clandestinely.route.model.LatLon;
import com.example.clandestinely.route.model.Node;
import com.example.clandestinely.route.model.Route;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.CopyrightOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.infowindow.InfoWindow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Draws the campus graph on an osmdroid map: a line for every path and a pin for every place.
 * Roofed paths are blue, stairs are orange, everything else is grey.
 * The chosen route (Week 6) is drawn on top as a thick navy line.
 * If we ever change map library, this class and RouteFragment's map setup are all that change.
 */
public final class CampusMapRenderer {

    private static final int COLOR_PATH = Color.argb(160, 110, 110, 110);
    private static final int COLOR_COVERED = Color.argb(230, 30, 136, 229);
    private static final int COLOR_STAIRS = Color.argb(230, 245, 124, 0);
    private static final int COLOR_ROUTE = Color.rgb(16, 40, 66);          // navy_deep from the team theme
    private static final int COLOR_ROUTE_BORDER = Color.WHITE;
    private static final String ROUTE_LINE_ID = "m4-route";

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

    /**
     * Draws the chosen route as a thick navy line with a white border on top of the campus paths,
     * and zooms the map so the whole route fits. Passing null (or an empty route) just removes
     * the previous route line.
     */
    public static void showRoute(MapView map, CampusGraph graph, Route route) {
        // 1. Remove the previous route line, if there is one.
        Iterator<Overlay> overlays = map.getOverlays().iterator();
        while (overlays.hasNext()) {
            Overlay overlay = overlays.next();
            if (overlay instanceof Polyline && ROUTE_LINE_ID.equals(((Polyline) overlay).getId())) {
                overlays.remove();
            }
        }
        if (route == null || route.getEdges().isEmpty()) {
            map.invalidate();
            return;
        }

        // 2. Draw the new one: a wide white line underneath, then the navy line on top.
        List<GeoPoint> points = pointsOf(route, graph);
        Polyline border = routeLine(map, points, COLOR_ROUTE_BORDER, 16f);
        Polyline line = routeLine(map, points, COLOR_ROUTE, 10f);
        // Put it above the grey paths but below the place pins, so the pins stay tappable.
        int position = Math.min(graph.getEdges().size(), map.getOverlays().size());
        map.getOverlays().add(position, border);
        map.getOverlays().add(position + 1, line);

        // 3. Zoom so the whole route is on screen (120 px of space around it).
        map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, 120);
        map.invalidate();
    }

    private static Polyline routeLine(MapView map, List<GeoPoint> points, int color, float width) {
        Polyline line = new Polyline(map);
        line.setPoints(points);
        line.setId(ROUTE_LINE_ID);
        line.getOutlinePaint().setColor(color);
        line.getOutlinePaint().setStrokeWidth(width);
        line.getOutlinePaint().setStrokeCap(Paint.Cap.ROUND);
        line.setInfoWindow((InfoWindow) null);   // no pop-up when tapped
        return line;
    }

    /**
     * Every point along the route, in walking order. An edge's shape may be stored in the
     * opposite direction to the way we walk it, so it is flipped when needed.
     */
    private static List<GeoPoint> pointsOf(Route route, CampusGraph graph) {
        List<GeoPoint> points = new ArrayList<>();
        List<String> nodeIds = route.getNodeIds();
        for (int i = 0; i < route.getEdges().size(); i++) {
            Edge edge = route.getEdges().get(i);
            List<GeoPoint> part = pointsOf(edge, graph);
            if (!edge.getFrom().equals(nodeIds.get(i))) {
                Collections.reverse(part);   // we walk this edge from its "to" end
            }
            if (!points.isEmpty() && !part.isEmpty()) {
                part.remove(0);              // don't repeat the point where two edges meet
            }
            points.addAll(part);
        }
        return points;
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
