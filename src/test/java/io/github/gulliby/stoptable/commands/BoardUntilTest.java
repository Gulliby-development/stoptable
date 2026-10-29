package io.github.gulliby.stoptable.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gulliby.stoptable.Cli;
import io.github.gulliby.stoptable.format.Io;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BoardUntilTest {
    private static final String VILLAGE = "stop HAR Harbor Pier\n"
            + "stop MKT Market Square\n"
            + "stop CHU Church Lane\n"
            + "stop PRK Park Gate\n"
            + "stop SCH School\n"
            + "service WD mon-fri\n"
            + "service WE sat,sun\n"
            + "trip 12 WD: HAR 07:00 > MKT 07:10 > CHU 07:18 > PRK 07:30\n"
            + "trip 12 WD: HAR 08:00 > MKT 08:10 > CHU 08:18 > PRK 08:30\n"
            + "trip 14 WD: MKT 07:12 > SCH 07:35 > PRK 07:50\n"
            + "trip 14 WD: MKT 08:15 > SCH 08:35 > PRK 08:50\n"
            + "trip 20 WE: HAR 09:00 > MKT 09:12 > PRK 09:40\n";

    private static Arguments row(String name, int status, String out, String err, String... args) {
        return Arguments.of(name, List.of(args), status, out, err);
    }

    static Stream<Arguments> rows() {
        return Stream.of(
                row("until keeps departures up to and including the time", 0,
                        "MKT Market Square, mon from 00:00 until 08:10\n"
                                + "time   route  to\n"
                                + "07:10  12     Park Gate\n"
                                + "07:12  14     Park Gate\n"
                                + "08:10  12     Park Gate\n",
                        "", "board", "MKT", "--until", "08:10", "village.tt"),
                row("until equal to after keeps a departure at that minute", 0,
                        "MKT Market Square, mon from 07:12 until 07:12\n"
                                + "time   route  to\n"
                                + "07:12  14     Park Gate\n",
                        "", "board", "MKT", "--after", "07:12", "--until=07:12", "village.tt"),
                row("a window between after and until", 0,
                        "HAR Harbor Pier, mon from 07:00 until 07:59\n"
                                + "time   route  to\n"
                                + "07:00  12     Park Gate\n",
                        "", "board", "--until", "07:59", "--after", "07:00", "HAR", "village.tt"),
                row("until before the first departure prints no departures and exits 1", 1,
                        "MKT Market Square, mon from 00:00 until 06:00\nno departures\n",
                        "", "board", "MKT", "--until", "06:00", "village.tt"),
                row("until on a day without service", 1,
                        "SCH School, sat from 00:00 until 12:00\nno departures\n",
                        "", "board", "--day", "sat", "SCH", "--until", "12:00", "village.tt"),
                row("the limit applies after the window and counts only what is left in it", 0,
                        "MKT Market Square, mon from 00:00 until 08:10\n"
                                + "time   route  to\n"
                                + "07:10  12     Park Gate\n"
                                + "+2 more\n",
                        "", "board", "MKT", "--until", "08:10", "--limit", "1", "village.tt"),
                row("the last minute of the day keeps everything", 0,
                        "MKT Market Square, mon from 00:00 until 23:59\n"
                                + "time   route  to\n"
                                + "07:10  12     Park Gate\n"
                                + "07:12  14     Park Gate\n"
                                + "+2 more\n",
                        "", "board", "MKT", "--until", "23:59", "--limit", "2", "village.tt"),
                row("until before after is a usage error", 2, "",
                        "stoptable: --until 07:00 is before --after 08:00\n",
                        "board", "MKT", "--after", "08:00", "--until", "07:00", "village.tt"),
                row("an unknown stop is reported before until is compared with after", 2, "",
                        "stoptable: unknown stop \"XXX\"\n",
                        "board", "XXX", "--after", "01:00", "--until", "00:00", "village.tt"),
                row("an invalid time", 2, "",
                        "stoptable: invalid --until \"24:00\" (expected a time from 00:00 to 23:59)\n",
                        "board", "MKT", "--until", "24:00", "village.tt"),
                row("a missing value", 2, "", "stoptable: --until needs a value\n",
                        "board", "MKT", "village.tt", "--until"),
                row("without until the header and rows are unchanged", 0,
                        "MKT Market Square, mon from 08:11\n"
                                + "time   route  to\n"
                                + "08:15  14     Park Gate\n",
                        "", "board", "MKT", "--after", "08:11", "village.tt"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void boardUntil(String name, List<String> args, int status, String out, String err) {
        Recorder io = new Recorder(VILLAGE);

        int actual = Cli.run(args, io);

        assertEquals(status, actual);
        assertEquals(out, io.out.toString());
        assertEquals(err, io.err.toString());
    }

    private static final class Recorder implements Io {
        private final byte[] file;
        private final StringBuilder out = new StringBuilder();
        private final StringBuilder err = new StringBuilder();

        private Recorder(String text) {
            file = text.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] readFile(String path) {
            return file;
        }

        @Override
        public byte[] readStdin() {
            return new byte[0];
        }

        @Override
        public void out(String text) {
            out.append(text);
        }

        @Override
        public void err(String text) {
            err.append(text);
        }
    }
}
