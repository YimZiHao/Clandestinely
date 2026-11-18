package com.example.clandestinely.ui.route;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.clandestinely.R;
import com.example.clandestinely.route.model.CampusGraph;
import com.example.clandestinely.route.model.Node;
import com.example.clandestinely.route.model.Route;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * M4 Route screen.
 * Week 5: shows the UM campus map with every place and path from campus_graph.json.
 * Week 6: pick From and To, and the Fastest route is drawn with its distance and walking time.
 * Intan's bottom navigation shows it with: new RouteFragment().
 */
public class RouteFragment extends Fragment {

    private static final GeoPoint UM_CENTRE = new GeoPoint(3.1235, 101.6555);
    private static final double START_ZOOM = 16.0;
    // OpenStreetMap's tile servers ask every app to identify itself honestly.
    private static final String USER_AGENT = "CuacaUM/0.1 (Universiti Malaya WIA2007 student project)";

    private MapView mapView;
    private RouteViewModel viewModel;

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
        TextView errorText = view.findViewById(R.id.route_error);
        View pickerCard = view.findViewById(R.id.route_picker_card);
        MaterialAutoCompleteTextView fromField = view.findViewById(R.id.route_from);
        MaterialAutoCompleteTextView toField = view.findViewById(R.id.route_to);
        TextView resultText = view.findViewById(R.id.route_result);

        mapView = view.findViewById(R.id.campus_map);
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        mapView.getController().setZoom(START_ZOOM);
        mapView.getController().setCenter(UM_CENTRE);

        viewModel = new ViewModelProvider(this).get(RouteViewModel.class);

        // 1. When the map data is loaded: draw it and fill the two pickers.
        viewModel.getGraph().observe(getViewLifecycleOwner(), graph -> {
            progress.setVisibility(View.GONE);
            CampusMapRenderer.draw(mapView, graph);
            List<Node> places = placesByName(graph);
            setUpPicker(fromField, places, viewModel::setFrom);
            setUpPicker(toField, places, viewModel::setTo);
            showPicked(fromField, graph, viewModel.getFromId());   // keeps the picks after rotating
            showPicked(toField, graph, viewModel.getToId());
            pickerCard.setVisibility(View.VISIBLE);
        });

        // 2. When a route is found: draw it and say how long it is.
        viewModel.getRoute().observe(getViewLifecycleOwner(), route -> {
            CampusGraph graph = viewModel.getGraph().getValue();
            if (graph != null) {
                CampusMapRenderer.showRoute(mapView, graph, route);
            }
            resultText.setText(describe(route));
        });

        viewModel.getLoadError().observe(getViewLifecycleOwner(), message -> {
            progress.setVisibility(View.GONE);
            errorText.setText(getString(R.string.route_load_failed, message));
            errorText.setVisibility(View.VISIBLE);
        });
    }

    /** The words under the pickers, e.g. "Fastest: 1,295 m · about 16 min walk". */
    private String describe(@Nullable Route route) {
        if (route == null) {
            return getString(R.string.route_not_found);
        }
        if (route.getEdges().isEmpty()) {
            return getString(R.string.route_same_place);
        }
        String metres = String.format(Locale.getDefault(), "%,d", Math.round(route.getDistanceM()));
        return getString(R.string.route_result, metres, route.getWalkingMinutes());
    }

    /** The 23 named places, sorted A to Z by name, for the pickers. */
    private static List<Node> placesByName(CampusGraph graph) {
        List<Node> places = new ArrayList<>(graph.getPlaces());
        Collections.sort(places, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return places;
    }

    /** Fills one picker with place names and tells the ViewModel which place was chosen. */
    private void setUpPicker(MaterialAutoCompleteTextView field, List<Node> places, Consumer<String> onPick) {
        List<String> names = new ArrayList<>();
        for (Node place : places) {
            names.add(place.getName());
        }
        field.setAdapter(new ShowAllAdapter(requireContext(), names));
        field.setOnItemClickListener((parent, item, position, id) -> onPick.accept(places.get(position).getId()));
    }

    /** Puts an already-picked place's name back into a picker (e.g. after rotating the phone). */
    private static void showPicked(MaterialAutoCompleteTextView field, CampusGraph graph, @Nullable String placeId) {
        Node place = placeId == null ? null : graph.getNode(placeId);
        if (place != null) {
            field.setText(place.getName(), false);   // false = don't filter the list
        }
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
        super.onDestroyView();
    }

    /**
     * A list adapter that always shows every place. (The normal one hides everything that
     * doesn't match the text already in the box, so after one pick you'd only see that one.)
     */
    private static final class ShowAllAdapter extends ArrayAdapter<String> {
        private final List<String> items;

        ShowAllAdapter(Context context, List<String> items) {
            super(context, android.R.layout.simple_list_item_1, items);
            this.items = items;
        }

        @NonNull
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = items;
                    results.count = items.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
            };
        }
    }
}
