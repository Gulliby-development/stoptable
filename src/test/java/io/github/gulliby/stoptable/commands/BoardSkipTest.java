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

class BoardSkipTest {
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
                row("skip 1 leaves out the first departure and the header says so", 0,
                        "MKT Market Square, mon from 00:00 skip 1\n"
                                + "time   route  to\n"
                                + "07:12  14     Park Gate\n"
                                + "08:10  12     Park Gate\n"
                                + "08:15  14     Park Gate\n",
                        "", "board", "MKT", "--skip", "1", "village.tt"),
                row("skip 0 is the default and prints the old header", 0,
                        "MKT Market Square, mon from 00:00\n"
                                + "time   route  to\n"
                                + "07:10  12     Park Gate\n"
                                + "07:12  14     Park Gate\n"
                                + "08:10  12     Park Gate\n"
                                + "08:15  14     Park Gate\n",
                        "", "board", "MKT", "--skip", "0", "village.tt"),
                row("skip 3 leaves the last departure", 0,
                        "MKT Market Square, mon from 00:00 skip 3\n"
                                + "time   route  to\n"
                                + "08:15  14     Park Gate\n",
                        "", "board", "MKT", "--skip", "3", "village.tt"),
                row("skip of all departures prints no departures and exits 1", 1,
                        "MKT Market Square, mon from 00:00 skip 4\nno departures\n",
                        "", "board", "MKT", "--skip", "4", "village.tt"),
                row("skip above the number of departures is the same", 1,
                        "MKT Market Square, mon from 00:00 skip 99\nno departures\n",
                        "", "board", "MKT", "--skip", "99", "village.tt"),
                row("the limit applies after the skip and more counts what follows", 0,
                        "MKT Market Square, mon from 00:00 skip 1\n"
                                + "time   route  to\n"
                                + "07:12  14     Park Gate\n"
                                + "08:10  12     Park Gate\n"
                                + "+1 more\n",
                        "", "board", "MKT", "--skip", "1", "--limit", "2", "village.tt"),
                row("skip 2 and limit 2 show the last two without a more line", 0,
                        "MKT Market Square, mon from 00:00 skip 2\n"
                                + "time   route  to\n"
                                + "08:10  12     Park Gate\n"
                                + "08:15  14     Park Gate\n",
                        "", "board", "MKT", "--skip", "2", "--limit", "2", "village.tt"),
                row("skip counts only the departures inside the until window", 0,
                        "MKT Market Square, mon from 00:00 until 08:10 skip 1\n"
                                + "time   route  to\n"
                                + "07:12  14     Park Gate\n"
                                + "08:10  12     Park Gate\n",
                        "", "board", "MKT", "--until", "08:10", "--skip", "1", "village.tt"),
                row("skip counts only the departures from after", 0,
                        "MKT Market Square, mon from 07:12 skip 1\n"
                                + "time   route  to\n"
                                + "08:10  12     Park Gate\n"
                                + "08:15  14     Park Gate\n",
                        "", "board", "MKT", "--after", "07:12", "--skip", "1", "village.tt"),
                row("the equals form is accepted", 0,
                        "HAR Harbor Pier, mon from 00:00 skip 1\n"
                                + "time   route  to\n"
                                + "08:00  12     Park Gate\n",
                        "", "board", "--skip=1", "HAR", "village.tt"),
                row("skip on a day with one departure", 1,
                        "MKT Market Square, sat from 00:00 skip 1\nno departures\n",
                        "", "board", "MKT", "--day", "sat", "--skip", "1", "village.tt"),
                row("skip above 99", 2, "",
                        "stoptable: invalid --skip \"100\" (expected an integer from 0 to 99)\n",
                        "board", "MKT", "--skip", "100", "village.tt"),
                row("a negative skip", 2, "",
                        "stoptable: invalid --skip \"-1\" (expected an integer from 0 to 99)\n",
                        "board", "MKT", "--skip", "-1", "village.tt"),
                row("a leading zero", 2, "",
                        "stoptable: invalid --skip \"01\" (expected an integer from 0 to 99)\n",
                        "board", "MKT", "--skip", "01", "village.tt"),
                row("a missing value", 2, "", "stoptable: --skip needs a value\n",
                        "board", "MKT", "village.tt", "--skip"),
                row("trip does not take the option", 2, "", "stoptable: unknown option \"--skip\"\n",
                        "trip", "HAR", "PRK", "--skip", "1", "village.tt"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void boardSkip(String name, List<String> args, int status, String out, String err) {
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
