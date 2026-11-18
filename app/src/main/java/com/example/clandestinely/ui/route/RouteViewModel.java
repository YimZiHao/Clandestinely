package com.example.clandestinely.ui.route;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.clandestinely.route.data.GraphRepository;
import com.example.clandestinely.route.model.CampusGraph;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Route screen's "brain". It loads the campus graph once (in the background) and keeps it
 * when the screen is rotated. The screen (RouteFragment) only watches it and draws.
 */
public class RouteViewModel extends AndroidViewModel {

    private final MutableLiveData<CampusGraph> graph = new MutableLiveData<>();
    private final MutableLiveData<String> loadError = new MutableLiveData<>();
    private final ExecutorService background = Executors.newSingleThreadExecutor();
    private final GraphRepository repository;

    public RouteViewModel(@NonNull Application application) {
        super(application);
        repository = new GraphRepository(application);
        loadGraph();
    }

    /** The loaded campus graph (empty until loading finishes). */
    public LiveData<CampusGraph> getGraph() { return graph; }

    /** A message if the graph could not be loaded. */
    public LiveData<String> getLoadError() { return loadError; }

    private void loadGraph() {
        background.execute(() -> {
            try {
                graph.postValue(repository.load());
            } catch (Exception e) {
                loadError.postValue(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        });
    }

    @Override
    protected void onCleared() {
        background.shutdownNow();
    }
}
