package com.example.clandestinely.route.data;

import android.content.Context;

import com.example.clandestinely.route.model.CampusGraph;
import com.example.clandestinely.route.model.Edge;
import com.example.clandestinely.route.model.LatLon;
import com.example.clandestinely.route.model.Node;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The only place that reads the campus map data.
 * It loads app/src/main/assets/campus_graph.json, which is a copy of data/campus_graph.json.
 */
public class GraphRepository {

    public static final String ASSET_NAME = "campus_graph.json";

    private final Context appContext;

    public GraphRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /** Reads and parses the file. Takes a moment, so call it off the main (screen) thread. */
    public CampusGraph load() throws IOException, JSONException {
        try (InputStream in = appContext.getAssets().open(ASSET_NAME)) {
            return parse(readAll(in));
        }
    }

    /** Turns the JSON text into a CampusGraph. Public so it can be unit-tested. */
    public static CampusGraph parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);

        List<Node> nodes = new ArrayList<>();
        JSONArray nodeArray = root.getJSONArray("nodes");
        for (int i = 0; i < nodeArray.length(); i++) {
            JSONObject o = nodeArray.getJSONObject(i);
            String id = o.getString("id");
            nodes.add(new Node(
                    id,
                    o.optString("name", id),
                    o.optString("group", ""),
                    o.getDouble("lat"),
                    o.getDouble("lon"),
                    o.optBoolean("verified", false)));
        }

        List<Edge> edges = new ArrayList<>();
        JSONArray edgeArray = root.optJSONArray("edges");
        if (edgeArray != null) {
            for (int i = 0; i < edgeArray.length(); i++) {
                JSONObject o = edgeArray.getJSONObject(i);
                edges.add(new Edge(
                        o.getString("id"),
                        o.getString("from"),
                        o.getString("to"),
                        o.getDouble("distance_m"),
                        boolOrNull(o, "covered"),
                        doubleOrNull(o, "shade_score"),
                        boolOrNull(o, "flood_prone"),
                        boolOrNull(o, "traffic_peak"),
                        boolOrNull(o, "step_free"),
                        readShadeByHour(o.optJSONObject("shade_by_hour")),
                        readGeometry(o.optJSONArray("geometry"))));
            }
        }
        return new CampusGraph(root.optString("version", "?"), nodes, edges);
    }

    // ---------- small helpers ----------

    /** JSON null or a missing key both become Java null ("unknown"). */
    private static Boolean boolOrNull(JSONObject o, String key) throws JSONException {
        return o.isNull(key) ? null : o.getBoolean(key);
    }

    private static Double doubleOrNull(JSONObject o, String key) throws JSONException {
        return o.isNull(key) ? null : o.getDouble(key);
    }

    private static Map<Integer, Double> readShadeByHour(JSONObject hours) throws JSONException {
        Map<Integer, Double> result = new HashMap<>();
        if (hours == null) {
            return result;
        }
        Iterator<String> keys = hours.keys();
        while (keys.hasNext()) {
            String hour = keys.next();
            result.put(Integer.parseInt(hour), hours.getDouble(hour));
        }
        return result;
    }

    private static List<LatLon> readGeometry(JSONArray points) throws JSONException {
        List<LatLon> result = new ArrayList<>();
        if (points == null) {
            return result;
        }
        for (int i = 0; i < points.length(); i++) {
            JSONArray p = points.getJSONArray(i);
            result.add(new LatLon(p.getDouble(0), p.getDouble(1)));
        }
        return result;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toString("UTF-8");
    }
}
