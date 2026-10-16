package io.github.gulliby.stoptable.commands;

import io.github.gulliby.stoptable.analysis.Board;
import io.github.gulliby.stoptable.core.Clock;
import io.github.gulliby.stoptable.core.Failure;
import io.github.gulliby.stoptable.core.Feed;
import io.github.gulliby.stoptable.core.Options;
import io.github.gulliby.stoptable.core.Text;
import io.github.gulliby.stoptable.format.Io;
import io.github.gulliby.stoptable.format.Loader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BoardCommand {
    private BoardCommand() {
    }

    public static int run(List<String> argv, Io io) {
        List<Options.Spec> specs = List.of(
                Options.Spec.choice("--day", Feed.DAYS, 0),
                Options.Spec.time("--after", 0),
                Options.Spec.integer("--limit", 1, 99, 10));
        Options.Parsed parsed = Options.parse(argv, specs);
        List<String> args = parsed.args();
        if (args.isEmpty()) {
            throw new Failure.Usage("missing STOP");
        }
        if (args.size() < 2) {
            throw new Failure.Usage("missing FILE");
        }
        if (args.size() > 2) {
            throw new Failure.Usage("too many arguments");
        }

        Loader.Loaded loaded = Loader.loadFeed(io, args.get(1));
        Feed feed = loaded.feed();
        String stopId = args.get(0);
        Feed.Stop stop = feed.stop(stopId);
        if (stop == null) {
            throw new Failure.Usage("unknown stop " + Text.show(stopId));
        }

        Map<String, Integer> values = parsed.values();
        int day = values.get("--day");
        int after = values.get("--after");
        int limit = values.get("--limit");
        List<Board.Row> rows = Board.rows(feed, stopId, day, after);

        StringBuilder output = new StringBuilder();
        output.append(stop.id()).append(' ').append(stop.name()).append(", ")
                .append(Feed.DAYS.get(day)).append(" from ").append(Clock.format(after)).append('\n');
        if (rows.isEmpty()) {
            output.append("no departures\n");
            io.out(output.toString());
            return 1;
        }

        List<List<String>> tableRows = new ArrayList<>();
        int displayed = Math.min(limit, rows.size());
        for (int index = 0; index < displayed; index++) {
            Board.Row row = rows.get(index);
            tableRows.add(List.of(Clock.format(row.minutes()), row.route(), row.to()));
        }
        for (String line : Text.renderTable(List.of("time", "route", "to"), tableRows)) {
            output.append(line).append('\n');
        }
        if (rows.size() > limit) {
            output.append('+').append(rows.size() - limit).append(" more\n");
        }

        io.out(output.toString());
        return 0;
    }
}
