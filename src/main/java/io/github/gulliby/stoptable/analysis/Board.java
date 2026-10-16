package io.github.gulliby.stoptable.analysis;

import io.github.gulliby.stoptable.core.Feed;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Board {
    private Board() {
    }

    public record Row(int minutes, String route, String to) {
    }

    public static List<Row> rows(Feed feed, String stop, int day, int after) {
        List<IndexedRow> departures = new ArrayList<>();
        for (int tripIndex = 0; tripIndex < feed.trips().size(); tripIndex++) {
            Feed.Trip trip = feed.trips().get(tripIndex);
            Feed.Service service = feed.service(trip.service());
            if (service == null || !service.runsOn(day) || trip.calls().size() < 2) {
                continue;
            }

            Feed.Call finalCall = trip.calls().get(trip.calls().size() - 1);
            Feed.Stop destination = feed.stop(finalCall.stop());
            for (int callIndex = 0; callIndex < trip.calls().size() - 1; callIndex++) {
                Feed.Call call = trip.calls().get(callIndex);
                if (call.stop().equals(stop) && call.minutes() >= after) {
                    departures.add(new IndexedRow(
                            new Row(call.minutes(), trip.route(), destination.name()), tripIndex));
                }
            }
        }

        departures.sort(Comparator
                .comparingInt((IndexedRow departure) -> departure.row().minutes())
                .thenComparing(departure -> departure.row().route())
                .thenComparingInt(IndexedRow::tripIndex));

        List<Row> result = new ArrayList<>(departures.size());
        for (IndexedRow departure : departures) {
            result.add(departure.row());
        }
        return List.copyOf(result);
    }

    private record IndexedRow(Row row, int tripIndex) {
    }
}
