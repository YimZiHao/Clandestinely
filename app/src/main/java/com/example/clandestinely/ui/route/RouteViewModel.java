package com.example.clandestinely.ui.route;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.clandestinely.route.data.GraphRepository;
import com.example.clandestinely.route.engine.EdgeCost;
import com.example.clandestinely.route.engine.RouteFinder;
import com.example.clandestinely.route.model.CampusGraph;
import com.example.clandestinely.route.model.Route;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Route screen's "brain". It loads the campus graph once (in the background), remembers
 * which places the user picked, and finds the route between them (also in the background).
 * It survives screen rotation. The screen (RouteFragment) only watches it and draws.
 */
public class RouteViewModel extends AndroidViewModel {

    private final MutableLiveData<CampusGraph> graph = new MutableLiveData<>();
    private final MutableLiveData<String> loadError = new MutableLiveData<>();
    private final MutableLiveData<Route> route = new MutableLiveData<>();
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final GraphRepository repository;

    private volatile RouteFinder finder;   // ready once the graph has loaded
    private String fromId;
    private String toId;

    public RouteViewModel(@NonNull Application application) {
        super(application);
        repository = new GraphRepository(application);
        loadGraph();
    }

    /** The loaded campus graph (empty until loading finishes). */
    public LiveData<CampusGraph> getGraph() { return graph; }

    /** A message if the graph could not be loaded. */
    public LiveData<String> getLoadError() { return loadError; }

    /**
     * The current route. Nothing until both places are picked; then a Route,
     * or null if there's no way to walk between them.
     */
    public LiveData<Route> getRoute() { return route; }

    public String getFromId() { return fromId; }
    public String getToId() { return toId; }

    /** Called when the user picks where they're starting. */
    public void setFrom(String placeId) {
        fromId = placeId;
        findRouteIfReady();
    }

    /** Called when the user picks where they're going. */
    public void setTo(String placeId) {
        toId = placeId;
        findRouteIfReady();
    }

    private void loadGraph() {
        background.execute(() -> {
            try {
                CampusGraph loaded = repository.load();
                finder = new RouteFinder(loaded);
                graph.postValue(loaded);
            } catch (Exception e) {
                loadError.postValue(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        });
    }

    /** Week 6: always the Fastest route (EdgeCost.DISTANCE). Week 7 adds Shadiest and Driest. */
    private void findRouteIfReady() {
        final String from = fromId;
        final String to = toId;
        final RouteFinder currentFinder = finder;
        if (from == null || to == null || currentFinder == null) {
            return;
        }
        background.execute(() -> route.postValue(currentFinder.find(from, to, EdgeCost.DISTANCE)));
    }

    @Override
    protected void onCleared() {
        background.shutdownNow();
    }
}
