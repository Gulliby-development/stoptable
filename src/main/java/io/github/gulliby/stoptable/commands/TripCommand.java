package io.github.gulliby.stoptable.commands;

import io.github.gulliby.stoptable.analysis.Scan;
import io.github.gulliby.stoptable.core.Clock;
import io.github.gulliby.stoptable.core.Failure;
import io.github.gulliby.stoptable.core.Feed;
import io.github.gulliby.stoptable.core.Options;
import io.github.gulliby.stoptable.core.Text;
import io.github.gulliby.stoptable.format.Io;
import io.github.gulliby.stoptable.format.Loader;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TripCommand {
    private TripCommand() {
    }

    public static int run(List<String> argv, Io io) {
        List<Options.Spec> specs = List.of(
                Options.Spec.choice("--day", Feed.DAYS, 0),
                Options.Spec.time("--after", 0),
                Options.Spec.integer("--transfer", 0, 120, 0));
        Options.Parsed parsed = Options.parse(argv, specs);
        List<String> args = parsed.args();
        if (args.isEmpty()) {
            throw new Failure.Usage("missing FROM");
        }
        if (args.size() < 2) {
            throw new Failure.Usage("missing TO");
        }
        if (args.size() < 3) {
            throw new Failure.Usage("missing FILE");
        }
        if (args.size() > 3) {
            throw new Failure.Usage("too many arguments");
        }

        Loader.Loaded loaded = Loader.loadFeed(io, args.get(2));
        Feed feed = loaded.feed();
        String from = args.get(0);
        String to = args.get(1);
        if (feed.stop(from) == null) {
            throw new Failure.Usage("unknown stop " + Text.show(from));
        }
        if (feed.stop(to) == null) {
            throw new Failure.Usage("unknown stop " + Text.show(to));
        }
        if (from.equals(to)) {
            throw new Failure.Usage("FROM and TO are the same stop");
        }

        Map<String, Integer> values = parsed.values();
        int day = values.get("--day");
        int after = values.get("--after");
        int transfer = values.get("--transfer");
        Optional<List<Scan.Leg>> result = Scan.journey(feed, from, to, day, after, transfer);

        StringBuilder output = new StringBuilder();
        output.append(from).append(" to ").append(to).append(", ")
                .append(Feed.DAYS.get(day)).append(" from ").append(Clock.format(after))
                .append(", transfer ").append(transfer).append(" min\n");
        if (result.isEmpty()) {
            output.append("no journey\n");
            io.out(output.toString());
            return 1;
        }

        List<Scan.Leg> legs = result.get();
        for (int index = 0; index < legs.size(); index++) {
            Scan.Leg leg = legs.get(index);
            output.append("leg ").append(index + 1).append(": route ").append(leg.route())
                    .append("  ").append(leg.from()).append(' ').append(Clock.format(leg.departs()))
                    .append(" > ").append(leg.to()).append(' ').append(Clock.format(leg.arrives()))
                    .append('\n');
        }
        int arrival = legs.get(legs.size() - 1).arrives();
        int changes = legs.size() - 1;
        output.append("arrive ").append(Clock.format(arrival)).append(", ")
                .append(arrival - after).append(" min, ").append(changes).append(' ')
                .append(Text.plural(changes, "change", "changes")).append('\n');

        io.out(output.toString());
        return 0;
    }
}
