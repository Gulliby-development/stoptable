package io.github.gulliby.stoptable.analysis;

import io.github.gulliby.stoptable.core.Feed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class Network {
    private Network() {
    }

    public static List<String> dot(Feed feed) {
        NetworkData data = collect(feed);
        List<String> lines = new ArrayList<>(data.nodes().size() + data.edges().size() + 3);
        lines.add("digraph stoptable {");
        lines.add("  rankdir=LR;");
        for (Feed.Stop stop : data.nodes()) {
            lines.add("  \"" + stop.id() + "\" [label=\"" + stop.name() + "\"];");
        }
        for (Edge edge : data.edges()) {
            lines.add("  \"" + edge.from() + "\" -> \"" + edge.to()
                    + "\" [label=\"" + edge.routes() + "\"];");
        }
        lines.add("}");
        return List.copyOf(lines);
    }

    public static List<String> mermaid(Feed feed) {
        NetworkData data = collect(feed);
        List<String> lines = new ArrayList<>(data.nodes().size() + data.edges().size() + 1);
        lines.add("graph LR");
        for (Feed.Stop stop : data.nodes()) {
            lines.add("  " + stop.id() + "[\"" + stop.name() + "\"]");
        }
        for (Edge edge : data.edges()) {
            lines.add("  " + edge.from() + " -->|" + edge.routes() + "| " + edge.to());
        }
        return List.copyOf(lines);
    }

    private static NetworkData collect(Feed feed) {
        Set<String> usedStops = new HashSet<>();
        for (Feed.Trip trip : feed.trips()) {
            for (Feed.Call call : trip.calls()) {
                usedStops.add(call.stop());
            }
        }

        List<Feed.Stop> nodes = new ArrayList<>();
        Map<String, Integer> declarationIndices = new HashMap<>();
        for (int index = 0; index < feed.stops().size(); index++) {
            Feed.Stop stop = feed.stops().get(index);
            declarationIndices.put(stop.id(), index);
            if (usedStops.contains(stop.id())) {
                nodes.add(stop);
            }
        }

        Comparator<EdgeKey> edgeOrder = Comparator
                .comparingInt(EdgeKey::fromIndex)
                .thenComparingInt(EdgeKey::toIndex);
        Map<EdgeKey, Set<String>> routesByEdge = new TreeMap<>(edgeOrder);
        for (Feed.Trip trip : feed.trips()) {
            for (int callIndex = 0; callIndex < trip.calls().size() - 1; callIndex++) {
                Feed.Call from = trip.calls().get(callIndex);
                Feed.Call to = trip.calls().get(callIndex + 1);
                Integer fromIndex = declarationIndices.get(from.stop());
                Integer toIndex = declarationIndices.get(to.stop());
                if (fromIndex == null || toIndex == null) {
                    continue;
                }
                EdgeKey edge = new EdgeKey(fromIndex, toIndex);
                routesByEdge.computeIfAbsent(edge, ignored -> new TreeSet<>()).add(trip.route());
            }
        }

        List<Edge> edges = new ArrayList<>(routesByEdge.size());
        for (Map.Entry<EdgeKey, Set<String>> entry : routesByEdge.entrySet()) {
            EdgeKey key = entry.getKey();
            edges.add(new Edge(
                    feed.stops().get(key.fromIndex()).id(),
                    feed.stops().get(key.toIndex()).id(),
                    String.join(",", entry.getValue())));
        }
        return new NetworkData(List.copyOf(nodes), List.copyOf(edges));
    }

    private record EdgeKey(int fromIndex, int toIndex) {
    }

    private record Edge(String from, String to, String routes) {
    }

    private record NetworkData(List<Feed.Stop> nodes, List<Edge> edges) {
    }
}
