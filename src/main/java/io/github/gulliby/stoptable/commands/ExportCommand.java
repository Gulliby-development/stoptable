package io.github.gulliby.stoptable.commands;

import io.github.gulliby.stoptable.analysis.Network;
import io.github.gulliby.stoptable.core.Failure;
import io.github.gulliby.stoptable.core.Options;
import io.github.gulliby.stoptable.format.Io;
import io.github.gulliby.stoptable.format.Loader;

import java.util.List;
import java.util.Map;

public final class ExportCommand {
    private ExportCommand() {
    }

    public static int run(List<String> argv, Io io) {
        Options.Parsed parsed = Options.parse(argv, List.of(
                Options.Spec.choice("--format", List.of("dot", "mermaid"), 0)));
        List<String> args = parsed.args();
        if (args.isEmpty()) {
            throw new Failure.Usage("missing FILE");
        }
        if (args.size() > 1) {
            throw new Failure.Usage("too many arguments");
        }

        Loader.Loaded loaded = Loader.loadFeed(io, args.get(0));
        Map<String, Integer> values = parsed.values();
        List<String> lines = values.get("--format") == 0
                ? Network.dot(loaded.feed())
                : Network.mermaid(loaded.feed());
        io.out(String.join("\n", lines) + "\n");
        return 0;
    }
}
