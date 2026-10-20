package io.github.gulliby.stoptable.analysis;

import io.github.gulliby.stoptable.core.Clock;
import io.github.gulliby.stoptable.core.Feed;
import io.github.gulliby.stoptable.core.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Check {
    private Check() {
    }

    public record Finding(int line, String code, String message) {
    }

    public static List<Finding> run(Feed feed) {
        List<Finding> findings = new ArrayList<>();
        Map<String, Integer> stopIndexes = new HashMap<>();
        Map<String, Feed.Stop> firstStopByName = new HashMap<>();
        Map<String, Feed.Service> servicesById = new HashMap<>();
        boolean[] usedStops = new boolean[feed.stops().size()];
        Set<String> usedServices = new HashSet<>();
        DisjointSet groups = new DisjointSet(feed.stops().size());

        for (int index = 0; index < feed.stops().size(); index++) {
            Feed.Stop stop = feed.stops().get(index);
            stopIndexes.put(stop.id(), index);

            String normalizedName = stop.name().toLowerCase(Locale.ROOT);
            Feed.Stop first = firstStopByName.putIfAbsent(normalizedName, stop);
            if (first != null) {
                findings.add(new Finding(
                        stop.line(),
                        "TT006",
                        "stop " + Text.show(stop.id())
                                + " has the same name as stop " + Text.show(first.id())
                                + " (line " + first.line() + ")"));
            }
        }
        for (Feed.Service service : feed.services()) {
            servicesById.put(service.id(), service);
        }

        Map<TripKey, Feed.Trip> firstTripByKey = new HashMap<>();
        Map<DepartureKey, List<Feed.Trip>> tripsByDeparture = new HashMap<>();

        for (Feed.Trip trip : feed.trips()) {
            usedServices.add(trip.service());
            TripKey key = keyOf(trip);
            Feed.Trip earlierRepeat = firstTripByKey.putIfAbsent(key, trip);
            if (earlierRepeat != null) {
                findings.add(new Finding(
                        trip.line(),
                        "TT003",
                        "trip repeats line " + earlierRepeat.line()));
            }

            if (!trip.calls().isEmpty()) {
                Feed.Call firstCall = trip.calls().get(0);
                DepartureKey departure = new DepartureKey(trip.route(), firstCall.stop(), firstCall.minutes());
                List<Feed.Trip> earlierDepartures = tripsByDeparture.computeIfAbsent(
                        departure, ignored -> new ArrayList<>());
                for (Feed.Trip earlier : earlierDepartures) {
                    if (key.equals(keyOf(earlier))) {
                        continue;
                    }
                    Feed.Service earlierService = servicesById.get(earlier.service());
                    Feed.Service currentService = servicesById.get(trip.service());
                    if (earlierService != null && currentService != null
                            && (earlierService.days() & currentService.days()) != 0) {
                        findings.add(new Finding(
                                trip.line(),
                                "TT004",
                                "route " + trip.route()
                                        + " also leaves stop " + Text.show(firstCall.stop())
                                        + " at " + Clock.format(firstCall.minutes())
                                        + " on a shared day (line " + earlier.line() + ")"));
                        break;
                    }
                }
                earlierDepartures.add(trip);
            }

            int previousStopIndex = -1;
            for (Feed.Call call : trip.calls()) {
                Integer stopIndex = stopIndexes.get(call.stop());
                if (stopIndex == null) {
                    previousStopIndex = -1;
                    continue;
                }
                usedStops[stopIndex] = true;
                if (previousStopIndex >= 0) {
                    groups.union(previousStopIndex, stopIndex);
                }
                previousStopIndex = stopIndex;
            }
        }

        for (int index = 0; index < feed.stops().size(); index++) {
            Feed.Stop stop = feed.stops().get(index);
            if (!usedStops[index]) {
                findings.add(new Finding(
                        stop.line(),
                        "TT001",
                        "stop " + Text.show(stop.id()) + " is used by no trip"));
            }
        }

        for (Feed.Service service : feed.services()) {
            if (!usedServices.contains(service.id())) {
                findings.add(new Finding(
                        service.line(),
                        "TT002",
                        "service " + Text.show(service.id()) + " is used by no trip"));
            }
        }

        addDisconnectedFindings(feed, usedStops, groups, findings);
        findings.sort((left, right) -> {
            int byLine = Integer.compare(left.line(), right.line());
            return byLine != 0 ? byLine : left.code().compareTo(right.code());
        });
        return List.copyOf(findings);
    }

    private static void addDisconnectedFindings(
            Feed feed, boolean[] usedStops, DisjointSet groups, List<Finding> findings) {
        int mainStop = -1;
        for (int index = 0; index < usedStops.length; index++) {
            if (usedStops[index]) {
                mainStop = index;
                break;
            }
        }
        if (mainStop < 0) {
            return;
        }

        int mainGroup = groups.find(mainStop);
        Set<Integer> reportedGroups = new HashSet<>();
        reportedGroups.add(mainGroup);
        Feed.Stop main = feed.stops().get(mainStop);
        for (int index = mainStop + 1; index < usedStops.length; index++) {
            if (!usedStops[index]) {
                continue;
            }
            int group = groups.find(index);
            if (reportedGroups.add(group)) {
                Feed.Stop stop = feed.stops().get(index);
                findings.add(new Finding(
                        stop.line(),
                        "TT005",
                        "stop " + Text.show(stop.id())
                                + " is not connected to stop " + Text.show(main.id())));
            }
        }
    }

    private static TripKey keyOf(Feed.Trip trip) {
        return new TripKey(trip.route(), trip.service(), trip.calls());
    }

    private record TripKey(String route, String service, List<Feed.Call> calls) {
    }

    private record DepartureKey(String route, String stop, int minutes) {
    }

    private static final class DisjointSet {
        private final int[] parents;
        private final byte[] ranks;

        private DisjointSet(int size) {
            parents = new int[size];
            ranks = new byte[size];
            for (int index = 0; index < size; index++) {
                parents[index] = index;
            }
        }

        private int find(int item) {
            if (parents[item] != item) {
                parents[item] = find(parents[item]);
            }
            return parents[item];
        }

        private void union(int left, int right) {
            int leftRoot = find(left);
            int rightRoot = find(right);
            if (leftRoot == rightRoot) {
                return;
            }
            if (ranks[leftRoot] < ranks[rightRoot]) {
                parents[leftRoot] = rightRoot;
            } else if (ranks[leftRoot] > ranks[rightRoot]) {
                parents[rightRoot] = leftRoot;
            } else {
                parents[rightRoot] = leftRoot;
                ranks[leftRoot]++;
            }
        }
    }
}
