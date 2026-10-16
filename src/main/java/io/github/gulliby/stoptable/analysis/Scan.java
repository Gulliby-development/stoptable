package io.github.gulliby.stoptable.analysis;

import io.github.gulliby.stoptable.core.Feed;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class Scan {
    private Scan() {
    }

    public record Leg(String route, String from, int departs, String to, int arrives) {
    }

    public static Optional<List<Leg>> journey(
            Feed feed, String from, String to, int day, int after, int transfer) {
        List<Connection> connections = new ArrayList<>();
        for (int tripIndex = 0; tripIndex < feed.trips().size(); tripIndex++) {
            Feed.Trip trip = feed.trips().get(tripIndex);
            Feed.Service service = feed.service(trip.service());
            if (service == null || !service.runsOn(day)) {
                continue;
            }
            for (int callIndex = 0; callIndex < trip.calls().size() - 1; callIndex++) {
                connections.add(new Connection(tripIndex, callIndex, trip));
            }
        }

        connections.sort(Comparator
                .comparingInt((Connection connection) ->
                        connection.trip().calls().get(connection.callIndex()).minutes())
                .thenComparingInt(Connection::tripIndex)
                .thenComparingInt(Connection::callIndex));

        Map<String, Integer> best = new HashMap<>();
        Map<String, Connection> into = new HashMap<>();
        int[] boarded = new int[feed.trips().size()];
        Arrays.fill(boarded, -1);

        for (Connection connection : connections) {
            int tripIndex = connection.tripIndex();
            int callIndex = connection.callIndex();
            Feed.Trip trip = connection.trip();
            Feed.Call departure = trip.calls().get(callIndex);
            Feed.Call arrival = trip.calls().get(callIndex + 1);

            if (boarded[tripIndex] < 0) {
                Integer bestAtDeparture = best.get(departure.stop());
                boolean canStartHere = departure.stop().equals(from) && departure.minutes() >= after;
                boolean canTransferHere = bestAtDeparture != null
                        && departure.minutes() >= bestAtDeparture + transfer;
                if (!canStartHere && !canTransferHere) {
                    continue;
                }
                boarded[tripIndex] = callIndex;
            }

            Integer previousBest = best.get(arrival.stop());
            if (previousBest == null || arrival.minutes() < previousBest) {
                best.put(arrival.stop(), arrival.minutes());
                into.put(arrival.stop(), connection);
            }
        }

        if (!best.containsKey(to)) {
            return Optional.empty();
        }

        List<Leg> reversed = new ArrayList<>();
        String current = to;
        while (!current.equals(from)) {
            Connection connection = into.get(current);
            if (connection == null) {
                return Optional.empty();
            }
            int firstCallIndex = boarded[connection.tripIndex()];
            Feed.Trip trip = connection.trip();
            Feed.Call firstCall = trip.calls().get(firstCallIndex);
            Feed.Call lastCall = trip.calls().get(connection.callIndex() + 1);
            reversed.add(new Leg(
                    trip.route(),
                    firstCall.stop(),
                    firstCall.minutes(),
                    lastCall.stop(),
                    lastCall.minutes()));
            current = firstCall.stop();
        }

        List<Leg> journey = new ArrayList<>(reversed);
        java.util.Collections.reverse(journey);
        return Optional.of(List.copyOf(journey));
    }

    private record Connection(int tripIndex, int callIndex, Feed.Trip trip) {
    }
}
