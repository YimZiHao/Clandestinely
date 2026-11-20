package com.example.clandestinely.ui.report;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import com.example.clandestinely.data.model.HazardReport;
import com.example.clandestinely.data.repository.HazardRepository;
import com.example.clandestinely.R;
import com.google.firebase.firestore.ListenerRegistration;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows active hazard reports as pins on the route map.
 * Kept separate from CampusMapRenderer because that class clears all overlays
 * each time it draws, so refresh() must be called after every draw.
 */
public class HazardMarkerManager {

    private final Context context;
    private final MapView mapView;
    private final HazardRepository repository;

    private final List<Marker> markers = new ArrayList<>();
    private List<HazardReport> latestReports = new ArrayList<>();
    private ListenerRegistration registration;

    public HazardMarkerManager(Context context, MapView mapView, HazardRepository repository) {
        this.context = context;
        this.mapView = mapView;
        this.repository = repository;
    }

    /** Start listening for active reports. Call from onStart(). */
    public void start() {
        if (registration != null) return;
        registration = repository.listenToActiveReports(new HazardRepository.ReportsListener() {
            @Override
            public void onReports(@NonNull List<HazardReport> reports) {
                latestReports = reports;
                refresh();
            }

            @Override
            public void onError(@NonNull Exception e) {
                // Pins just stay as they are if the listener fails.
            }
        });
    }

    /** Stop listening. Call from onStop(). */
    public void stop() {
        if (registration != null) {
            registration.remove();
            registration = null;
        }
    }

    /** Re-adds all hazard pins to the map. Safe to call any number of times. */
    public void refresh() {
        mapView.getOverlays().removeAll(markers);
        markers.clear();

        Drawable icon = ContextCompat.getDrawable(context, R.drawable.ic_hazard_pin);

        for (HazardReport report : latestReports) {
            if (report.getLocation() == null) continue;

            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(
                    report.getLocation().getLatitude(),
                    report.getLocation().getLongitude()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(icon);
            marker.setInfoWindow(new MarkerInfoWindow(R.layout.hazard_info_window, mapView));
            marker.setTitle(categoryLabel(report.getCategory()));
            String description = report.getDescription();
            if (description != null && !description.trim().isEmpty()) {
                marker.setSnippet(description);
            }

            markers.add(marker);
            mapView.getOverlays().add(marker);
        }
        mapView.invalidate();
    }

    private static String categoryLabel(String category) {
        if (category == null) return "Hazard";
        switch (category) {
            case "flood": return "Flood";
            case "fallen_tree": return "Fallen tree";
            case "blocked_drain": return "Blocked drain";
            default: return "Other hazard";
        }
    }
}