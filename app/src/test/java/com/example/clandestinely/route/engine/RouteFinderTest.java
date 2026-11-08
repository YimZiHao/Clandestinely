package com.example.clandestinely.route.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.clandestinely.route.data.GraphRepository;
import com.example.clandestinely.route.model.CampusGraph;
import com.example.clandestinely.route.model.Edge;
import com.example.clandestinely.route.model.LatLon;
import com.example.clandestinely.route.model.Node;
import com.example.clandestinely.route.model.Route;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Checks the RouteFinder, first on a tiny made-up map, then on the real campus graph.
 * The real-graph tests need org.json, which app/build.gradle.kts already has for unit tests.
 */
public class RouteFinderTest {

    // ---------- a tiny made-up map ----------
    //
    //   A ---100--- B ---100--- C        D (on its own, no paths)
    //   \______________300_____/

    private static CampusGraph tinyMap() {
        List<Node> nodes = Arrays.asList(
                new Node("A", "Place A", "core", 3.0, 101.0, true),
                new Node("B", "Place B", "core", 3.0, 101.001, true),
                new Node("C", "Place C", "core", 3.0, 101.002, true),
                new Node("D", "Place D", "core", 3.0, 101.003, true));
        List<Edge> edges = Arrays.asList(
                edge("A-B", "A", "B", 100),
                edge("B-C", "B", "C", 100),
                edge("A-C", "A", "C", 300));
        return new CampusGraph("test", nodes, edges);
    }

    private static Edge edge(String id, String from, String to, double metres) {
        return new Edge(id, from, to, metres, null, null, null, null, null,
                Collections.<Integer, Double>emptyMap(), Collections.<LatLon>emptyList());
    }

    @Test
    public void picksTheShorterWayRoundEvenWithMoreSteps() {
        Route route = new RouteFinder(tinyMap()).find("A", "C", EdgeCost.DISTANCE);
        assertNotNull(route);
        assertEquals(200.0, route.getDistanceM(), 0.001);              // A-B-C, not the direct 300 m
        assertEquals(Arrays.asList("A", "B", "C"), route.getNodeIds());
    }

    @Test
    public void worksInBothDirections() {
        Route route = new RouteFinder(tinyMap()).find("C", "A", EdgeCost.DISTANCE);
        assertNotNull(route);
        assertEquals(Arrays.asList("C", "B", "A"), route.getNodeIds());
    }

    @Test
    public void sameStartAndDestinationIsAnEmptyRoute() {
        Route route = new RouteFinder(tinyMap()).find("B", "B", EdgeCost.DISTANCE);
        assertNotNull(route);
        assertEquals(0.0, route.getDistanceM(), 0.001);
        assertTrue(route.getEdges().isEmpty());
        assertEquals(0, route.getWalkingMinutes());
    }

    @Test
    public void noRouteToAPlaceWithNoPaths() {
        assertNull(new RouteFinder(tinyMap()).find("A", "D", EdgeCost.DISTANCE));
    }

    @Test
    public void unknownPlaceGivesNoRoute() {
        assertNull(new RouteFinder(tinyMap()).find("A", "NOPE", EdgeCost.DISTANCE));
    }

    @Test
    public void aDifferentCostRuleCanPickADifferentRoute() {
        // Pretend B-C is flooded: make it very expensive. Now the direct A-C path wins.
        EdgeCost avoidBC = edge -> edge.getId().equals("B-C") ? 1_000_000 : edge.getDistanceM();
        Route route = new RouteFinder(tinyMap()).find("A", "C", avoidBC);
        assertNotNull(route);
        assertEquals(Arrays.asList("A", "C"), route.getNodeIds());
        assertEquals(300.0, route.getDistanceM(), 0.001);
    }

    // ---------- the real campus graph ----------

    private static CampusGraph realMap() throws Exception {
        // Unit tests run from the app/ folder.
        try (InputStream in = new FileInputStream("src/main/assets/" + GraphRepository.ASSET_NAME)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return GraphRepository.parse(new String(out.toByteArray(), "UTF-8"));
        }
    }

    @Test
    public void fsktmToMainLibraryIsAbout1295Metres() throws Exception {
        Route route = new RouteFinder(realMap()).find("FSKTM", "LIB", EdgeCost.DISTANCE);
        assertNotNull(route);
        assertEquals(1295.0, route.getDistanceM(), 10.0);   // matches the Python check of the data
        assertEquals(16, route.getWalkingMinutes());         // 1,295 m at 80 m per minute
    }

    @Test
    public void everyPlaceCanReachEveryOtherPlace() throws Exception {
        CampusGraph graph = realMap();
        RouteFinder finder = new RouteFinder(graph);
        List<String> missing = new ArrayList<>();
        for (Node a : graph.getPlaces()) {
            for (Node b : graph.getPlaces()) {
                if (finder.find(a.getId(), b.getId(), EdgeCost.DISTANCE) == null) {
                    missing.add(a.getId() + "->" + b.getId());
                }
            }
        }
        assertTrue("No route for: " + missing, missing.isEmpty());
    }

    @Test
    public void routeStepsJoinUpEndToEnd() throws Exception {
        Route route = new RouteFinder(realMap()).find("KK8", "DTC", EdgeCost.DISTANCE);
        assertNotNull(route);
        List<String> nodes = route.getNodeIds();
        assertEquals("KK8", nodes.get(0));
        assertEquals("DTC", nodes.get(nodes.size() - 1));
        for (int i = 0; i < route.getEdges().size(); i++) {
            Edge edge = route.getEdges().get(i);
            String a = nodes.get(i);
            String b = nodes.get(i + 1);
            assertTrue("edge " + edge.getId() + " should join " + a + " and " + b,
                    (edge.getFrom().equals(a) && edge.getTo().equals(b))
                            || (edge.getFrom().equals(b) && edge.getTo().equals(a)));
        }
    }
}
