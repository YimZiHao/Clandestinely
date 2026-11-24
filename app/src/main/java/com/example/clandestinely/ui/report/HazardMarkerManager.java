package com.example.clandestinely.ui.report;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import com.example.clandestinely.data.model.HazardReport;
import com.example.clandestinely.data.repository.HazardRepository;
import com.example.clandestinely.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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

    private static final long EXPIRY_CHECK_MS = 60_000; // 1 minute
    private final Context context;
    private final MapView mapView;
    private final HazardRepository repository;

    private final List<Marker> markers = new ArrayList<>();
    private List<HazardReport> latestReports = new ArrayList<>();
    private ListenerRegistration registration;

    // Re-checks once a minute so pins that run out of time disappear.
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable expiryCheck = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            List<HazardReport> stillActive = new ArrayList<>();
            for (HazardReport report : latestReports) {
                if (report.isActiveAt(now)) stillActive.add(report);
            }
            if (stillActive.size() != latestReports.size()) {
                latestReports = stillActive;
                refresh();
            }
            handler.postDelayed(this, EXPIRY_CHECK_MS);
        }
    };

    public HazardMarkerManager(Context context, MapView mapView, HazardRepository repository) {
        this.context = context;
        this.mapView = mapView;
        this.repository = repository;
    }

    /** Start listening for active reports. Call from onStart(). */
    public void start() {
        if (registration != null) return;
        registration = repository.listenToActiveReports(new HazardRepository.ReportsListener() {
            public void onReports(@NonNull List<HazardReport> reports) {
                android.util.Log.d("HazardMarkers", "Received " + reports.size() + " active reports");
                latestReports = reports;
                refresh();
            }

            public void onError(@NonNull Exception e) {
                android.util.Log.e("HazardMarkers", "Listener failed", e);
            }
        });
        handler.removeCallbacks(expiryCheck);
        handler.postDelayed(expiryCheck, EXPIRY_CHECK_MS);
    }

    /** Stop listening. Call from onStop(). */
    public void stop() {
        handler.removeCallbacks(expiryCheck);
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
        long now = System.currentTimeMillis();

        for (HazardReport report : latestReports) {
            if (report.getLocation() == null) continue;
            if (!report.isActiveAt(now)) continue;

            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(
                    report.getLocation().getLatitude(),
                    report.getLocation().getLongitude()));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(icon);
            marker.setInfoWindow(createInfoWindow(report));
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

    /** The popup for one report, including the Confirm button. */
    private MarkerInfoWindow createInfoWindow(final HazardReport report) {
        return new MarkerInfoWindow(R.layout.hazard_info_window, mapView) {
            @Override
            public void onOpen(Object item) {
                super.onOpen(item);

                final Button confirmButton = mView.findViewById(R.id.bubble_confirm);
                final FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                // Hide for guests, signed-out users and the report's own author.
                boolean canConfirm = user != null
                        && !user.isAnonymous()
                        && !user.getUid().equals(report.getReportedBy());
                confirmButton.setVisibility(canConfirm ? View.VISIBLE : View.GONE);
                if (!canConfirm) return;

                confirmButton.setEnabled(true);
                confirmButton.setOnClickListener(v -> {
                    confirmButton.setEnabled(false);
                    repository.confirmReport(report.getId(), user.getUid())
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(context, "Report confirmed", Toast.LENGTH_SHORT).show();
                                close();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(context, HazardRepository.friendlyMessage(e),
                                        Toast.LENGTH_LONG).show();
                                confirmButton.setEnabled(true);
                            });
                });
            }
        };
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