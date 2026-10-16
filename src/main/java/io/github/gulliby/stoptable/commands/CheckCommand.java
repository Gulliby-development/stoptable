package io.github.gulliby.stoptable.commands;

import io.github.gulliby.stoptable.analysis.Check;
import io.github.gulliby.stoptable.core.Failure;
import io.github.gulliby.stoptable.core.Feed;
import io.github.gulliby.stoptable.core.Options;
import io.github.gulliby.stoptable.core.Text;
import io.github.gulliby.stoptable.format.Io;
import io.github.gulliby.stoptable.format.Loader;

import java.util.ArrayList;
import java.util.List;

public final class CheckCommand {
    private CheckCommand() {
    }

    public static int run(List<String> argv, Io io) {
        Options.Parsed parsed = Options.parse(argv, List.of());
        List<String> args = parsed.args();
        if (args.isEmpty()) {
            throw new Failure.Usage("missing FILE");
        }
        if (args.size() > 1) {
            throw new Failure.Usage("too many arguments");
        }

        Loader.Loaded loaded = Loader.loadFeed(io, args.get(0));
        Feed feed = loaded.feed();
        List<Check.Finding> findings = Check.run(feed);
        StringBuilder output = new StringBuilder();
        if (findings.isEmpty()) {
            output.append("ok, ")
                    .append(feed.stops().size()).append(' ')
                    .append(Text.plural(feed.stops().size(), "stop", "stops")).append(", ")
                    .append(feed.services().size()).append(' ')
                    .append(Text.plural(feed.services().size(), "service", "services")).append(", ")
                    .append(feed.trips().size()).append(' ')
                    .append(Text.plural(feed.trips().size(), "trip", "trips")).append('\n');
            io.out(output.toString());
            return 0;
        }

        for (Check.Finding finding : findings) {
            output.append(loaded.display()).append(':')
                    .append(finding.line()).append(": ")
                    .append(finding.code()).append(' ')
                    .append(finding.message()).append('\n');
        }
        output.append(findings.size()).append(' ')
                .append(Text.plural(findings.size(), "finding", "findings")).append('\n');
        io.out(output.toString());
        return 1;
    }
}
