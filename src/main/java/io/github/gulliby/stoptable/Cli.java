package io.github.gulliby.stoptable;

import io.github.gulliby.stoptable.commands.BoardCommand;
import io.github.gulliby.stoptable.commands.CheckCommand;
import io.github.gulliby.stoptable.commands.ExportCommand;
import io.github.gulliby.stoptable.commands.TripCommand;
import io.github.gulliby.stoptable.core.Failure;
import io.github.gulliby.stoptable.core.Text;
import io.github.gulliby.stoptable.format.Io;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public final class Cli {
    public static final String USAGE = "usage: stoptable check FILE\n"
            + "       stoptable board [--day DAY] [--after HH:MM] [--limit N] STOP FILE\n"
            + "       stoptable trip [--day DAY] [--after HH:MM] [--transfer N] FROM TO FILE\n"
            + "       stoptable export [--format dot|mermaid] FILE";

    private static final Map<String, BiFunction<List<String>, Io, Integer>> COMMANDS = Map.of(
            "check", CheckCommand::run,
            "board", BoardCommand::run,
            "trip", TripCommand::run,
            "export", ExportCommand::run);

    private Cli() {
    }

    public static int run(List<String> argv, Io io) {
        try {
            if (argv.isEmpty()) {
                throw new Failure.Usage("missing command (check, board, trip or export)");
            }
            if (argv.size() == 1 && argv.get(0).equals("--help")) {
                io.out(USAGE + "\n");
                return 0;
            }

            BiFunction<List<String>, Io, Integer> command = COMMANDS.get(argv.get(0));
            if (command == null) {
                throw new Failure.Usage("unknown command " + Text.show(argv.get(0)));
            }
            return command.apply(argv.subList(1, argv.size()), io);
        } catch (Failure.Usage failure) {
            io.err("stoptable: " + failure.getMessage() + "\n");
            return 2;
        }
    }
}
