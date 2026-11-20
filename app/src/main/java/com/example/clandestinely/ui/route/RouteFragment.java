package com.example.clandestinely.ui.route;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.clandestinely.R;
import com.example.clandestinely.data.repository.HazardRepository;
import com.example.clandestinely.ui.report.HazardMarkerManager;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;

import java.io.File;

/**
 * M4 Route screen. Week 5: shows the UM campus map with every place and path from campus_graph.json.
 * Intan's bottom navigation can show it with: new RouteFragment().
 */
public class RouteFragment extends Fragment {

    private static final GeoPoint UM_CENTRE = new GeoPoint(3.1235, 101.6555);
    private static final double START_ZOOM = 16.0;
    // OpenStreetMap's tile servers ask every app to identify itself honestly.
    private static final String USER_AGENT = "CuacaUM/0.1 (Universiti Malaya WIA2007 student project)";

    private MapView mapView;
    private HazardMarkerManager hazardMarkers;

    public RouteFragment() {
        super(R.layout.fragment_route);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // osmdroid must be configured before its MapView is created.
        IConfigurationProvider config = Configuration.getInstance();
        config.setUserAgentValue(USER_AGENT);
        File base = new File(requireContext().getCacheDir(), "osmdroid");
        config.setOsmdroidBasePath(base);
        config.setOsmdroidTileCache(new File(base, "tiles"));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ProgressBar progress = view.findViewById(R.id.route_progress);
        View summaryCard = view.findViewById(R.id.route_summary_card);
        TextView summary = view.findViewById(R.id.route_summary);
        TextView errorText = view.findViewById(R.id.route_error);

        mapView = view.findViewById(R.id.campus_map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.getController().setZoom(START_ZOOM);
        mapView.getController().setCenter(UM_CENTRE);
        hazardMarkers = new HazardMarkerManager(requireContext(), mapView, new HazardRepository());

        RouteViewModel viewModel = new ViewModelProvider(this).get(RouteViewModel.class);

        viewModel.getGraph().observe(getViewLifecycleOwner(), graph -> {
            progress.setVisibility(View.GONE);
            CampusMapRenderer.draw(mapView, graph);
            hazardMarkers.refresh();
            summary.setText(getString(R.string.route_summary,
                    graph.getPlaces().size(), graph.getEdges().size()));
            summaryCard.setVisibility(View.VISIBLE);
        });

        viewModel.getLoadError().observe(getViewLifecycleOwner(), message -> {
            progress.setVisibility(View.GONE);
            errorText.setText(getString(R.string.route_load_failed, message));
            errorText.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (hazardMarkers != null) hazardMarkers.start();
    }

    @Override
    public void onStop() {
        if (hazardMarkers != null) hazardMarkers.stop();
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (mapView != null) {
            mapView.onDetach();
            mapView = null;
        }
        hazardMarkers = null;
        super.onDestroyView();
    }
}
