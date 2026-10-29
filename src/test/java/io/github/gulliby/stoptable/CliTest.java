package io.github.gulliby.stoptable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.stoptable.format.Io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CliTest {
    private static final String USAGE = "usage: stoptable check FILE\n"
            + "       stoptable board [--day DAY] [--after HH:MM] [--until HH:MM] [--limit N] STOP FILE\n"
            + "       stoptable trip [--day DAY] [--after HH:MM] [--transfer N] FROM TO FILE\n"
            + "       stoptable export [--format dot|mermaid] FILE";

    private static final String SIMPLE = "stop A Alpha\n"
            + "stop B Beta\n"
            + "service WD mon-fri\n"
            + "trip R WD: A 08:00 > B 08:10\n";

    @Test
    void standaloneHelpPrintsTheExactUsageAndDoesNotReadInput() {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(List.of("--help"), io);

        assertEquals(USAGE, Cli.USAGE);
        assertEquals(0, status);
        assertEquals(USAGE + "\n", io.outText);
        assertEquals("", io.errText);
        assertEquals(1, io.outCalls);
        assertEquals(0, io.errCalls);
        assertEquals(0, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals(List.of("out"), io.events);
    }

    @Test
    void missingCommandHasExactUsageDiagnosticAndNoOutput() {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(List.of(), io);

        assertUsageWithoutRead(status, io, "missing command (check, board, trip or export)");
    }

    @Test
    void unknownCommandSanitizesSupplementaryCharactersAsOneCodePoint() {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(List.of("🛶"), io);

        assertUsageWithoutRead(status, io, "unknown command \"?\"");
    }

    @Test
    void helpIsNotACommandWhenItIsNotTheOnlyArgument() {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(List.of("--help", "check"), io);

        assertUsageWithoutRead(status, io, "unknown command \"--help\"");
    }

    @Test
    void helpAfterACommandIsAnUnknownOption() {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(List.of("check", "--help"), io);

        assertUsageWithoutRead(status, io, "unknown option \"--help\"");
    }

    @Test
    void dispatcherRoutesEveryCommandToItsExpectedLiteralOutput() {
        FakeIo checkIo = new FakeIo(SIMPLE);
        int checkStatus = Cli.run(List.of("check", "feed.tt"), checkIo);
        assertResult(checkStatus, checkIo, 0,
                "ok, 2 stops, 1 service, 1 trip\n", List.of("readFile", "out"));

        FakeIo boardIo = new FakeIo(SIMPLE);
        int boardStatus = Cli.run(List.of("board", "A", "feed.tt"), boardIo);
        assertResult(boardStatus, boardIo, 0,
                "A Alpha, mon from 00:00\n"
                        + "time   route  to\n"
                        + "08:00  R      Beta\n",
                List.of("readFile", "out"));

        FakeIo tripIo = new FakeIo(SIMPLE);
        int tripStatus = Cli.run(List.of("trip", "A", "B", "feed.tt"), tripIo);
        assertResult(tripStatus, tripIo, 0,
                "A to B, mon from 00:00, transfer 0 min\n"
                        + "leg 1: route R  A 08:00 > B 08:10\n"
                        + "arrive 08:10, 490 min, 0 changes\n",
                List.of("readFile", "out"));

        FakeIo exportIo = new FakeIo(SIMPLE);
        int exportStatus = Cli.run(List.of("export", "feed.tt"), exportIo);
        assertResult(exportStatus, exportIo, 0,
                "digraph stoptable {\n"
                        + "  rankdir=LR;\n"
                        + "  \"A\" [label=\"Alpha\"];\n"
                        + "  \"B\" [label=\"Beta\"];\n"
                        + "  \"A\" -> \"B\" [label=\"R\"];\n"
                        + "}\n",
                List.of("readFile", "out"));
    }

    @Test
    void optionsMayBeInterspersedRepeatedAndUseEqualsWhileDashRemainsStdin() {
        FakeIo io = new FakeIo(new byte[0], SIMPLE.getBytes(StandardCharsets.UTF_8));

        int status = Cli.run(List.of(
                "board", "--limit=bad", "A", "--day=mon", "-", "--after", "07:59", "--limit=1"), io);

        assertResult(status, io, 0,
                "A Alpha, mon from 07:59\n"
                        + "time   route  to\n"
                        + "08:00  R      Beta\n",
                List.of("readStdin", "out"));
        assertEquals(0, io.fileReads);
        assertEquals(1, io.stdinReads);
        assertNull(io.requestedPath);
    }

    @Test
    void equalsValueSplitsAtFirstEqualsAndReportsTheWholeInvalidValue() {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(List.of("export", "--format=dot=mermaid"), io);

        assertUsageWithoutRead(status, io,
                "invalid --format \"dot=mermaid\" (expected dot or mermaid)");
    }

    @ParameterizedTest
    @MethodSource("invalidOptionInputs")
    void validatesOptionsBeforeArityAndUsesExactDiagnostics(List<String> argv, String message) {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(argv, io);

        assertUsageWithoutRead(status, io, message);
    }

    private static Stream<Arguments> invalidOptionInputs() {
        return Stream.of(
                Arguments.of(List.of("board", "--after", "7:11"),
                        "invalid --after \"7:11\" (expected a time from 00:00 to 23:59)"),
                Arguments.of(List.of("board", "--after", "24:00"),
                        "invalid --after \"24:00\" (expected a time from 00:00 to 23:59)"),
                Arguments.of(List.of("board", "--after", "０８:００"),
                        "invalid --after \"??:??\" (expected a time from 00:00 to 23:59)"),
                Arguments.of(List.of("board", "--limit", "+3"),
                        "invalid --limit \"+3\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("board", "--limit", "-1"),
                        "invalid --limit \"-1\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("board", "--limit", "007"),
                        "invalid --limit \"007\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("board", "--limit", "1.5"),
                        "invalid --limit \"1.5\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("board", "--limit", "0"),
                        "invalid --limit \"0\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("board", "--limit", "100"),
                        "invalid --limit \"100\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("trip", "--transfer", "121"),
                        "invalid --transfer \"121\" (expected an integer from 0 to 120)"),
                Arguments.of(List.of("board", "--day", "FRI"),
                        "invalid --day \"FRI\" (expected mon, tue, wed, thu, fri, sat or sun)"),
                Arguments.of(List.of("export", "--format", "image"),
                        "invalid --format \"image\" (expected dot or mermaid)"),
                Arguments.of(List.of("board", "--after", "bad", "--unknown"),
                        "unknown option \"--unknown\""),
                Arguments.of(List.of("board", "--after", "bad", "--limit"),
                        "--limit needs a value"),
                Arguments.of(List.of("board", "--limit", "bad", "--after", "7:11"),
                        "invalid --after \"7:11\" (expected a time from 00:00 to 23:59)"),
                Arguments.of(List.of("board", "--after", "7:11", "--day", "bad"),
                        "invalid --day \"bad\" (expected mon, tue, wed, thu, fri, sat or sun)"),
                Arguments.of(List.of("board", "--limit", "--help"),
                        "invalid --limit \"--help\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("export", "--unknown"),
                        "unknown option \"--unknown\""),
                Arguments.of(List.of("export", "--format"),
                        "--format needs a value"));
    }

    @ParameterizedTest
    @MethodSource("invalidArityInputs")
    void checksEachCommandsArityBeforeReading(List<String> argv, String message) {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(argv, io);

        assertUsageWithoutRead(status, io, message);
    }

    private static Stream<Arguments> invalidArityInputs() {
        return Stream.of(
                Arguments.of(List.of("check"), "missing FILE"),
                Arguments.of(List.of("check", "a.tt", "b.tt"), "too many arguments"),
                Arguments.of(List.of("board"), "missing STOP"),
                Arguments.of(List.of("board", "A"), "missing FILE"),
                Arguments.of(List.of("board", "A", "a.tt", "extra"), "too many arguments"),
                Arguments.of(List.of("trip"), "missing FROM"),
                Arguments.of(List.of("trip", "A"), "missing TO"),
                Arguments.of(List.of("trip", "A", "B"), "missing FILE"),
                Arguments.of(List.of("trip", "A", "B", "a.tt", "extra"), "too many arguments"),
                Arguments.of(List.of("export"), "missing FILE"),
                Arguments.of(List.of("export", "a.tt", "extra"), "too many arguments"));
    }

    @ParameterizedTest
    @MethodSource("validDays")
    void acceptsEveryDayAndTheUpperTimeAndLimitBounds(String day) {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(List.of("board", "--day=" + day, "--after=23:59", "--limit=99", "A", "feed.tt"), io);

        assertResult(status, io, 1, "A Alpha, " + day + " from 23:59\nno departures\n",
                List.of("readFile", "out"));
    }

    private static Stream<Arguments> validDays() {
        return Stream.of(
                Arguments.of("mon"),
                Arguments.of("tue"),
                Arguments.of("wed"),
                Arguments.of("thu"),
                Arguments.of("fri"),
                Arguments.of("sat"),
                Arguments.of("sun"));
    }

    @Test
    void acceptsLowerOptionBoundsAndUsesTheLastValidRepetition() {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(List.of("board", "--day", "mon", "--after=00:00",
                "--limit=99", "A", "feed.tt", "--limit", "1"), io);

        assertResult(status, io, 0,
                "A Alpha, mon from 00:00\n"
                        + "time   route  to\n"
                        + "08:00  R      Beta\n",
                List.of("readFile", "out"));
    }

    @Test
    void readsFilesAndNormalizesDisplayedPathsWhenIoReportsReadFailure() {
        FakeIo io = new FakeIo(SIMPLE);
        io.fileFailure = new IOException("private details");

        int status = Cli.run(List.of("check", "C:\\feeds\\bad.tt"), io);

        assertUsage(status, io, "C:/feeds/bad.tt: cannot read the file");
        assertEquals(1, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals("C:\\feeds\\bad.tt", io.requestedPath);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void mapsInvalidPathExceptionsToTheSameSafeReadDiagnostic() {
        FakeIo io = new FakeIo(SIMPLE);
        io.filePathFailure = new InvalidPathException("bad", "invalid character");

        int status = Cli.run(List.of("check", "bad.tt"), io);

        assertUsage(status, io, "bad.tt: cannot read the file");
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void mapsStdinReadFailureWithoutAttemptingAFileRead() {
        FakeIo io = new FakeIo(new byte[0], new byte[0]);
        io.stdinFailure = new IOException("private details");

        int status = Cli.run(List.of("check", "-"), io);

        assertUsage(status, io, "<stdin>: cannot read the file");
        assertEquals(0, io.fileReads);
        assertEquals(1, io.stdinReads);
        assertNull(io.requestedPath);
        assertEquals(List.of("readStdin", "err"), io.events);
    }

    @Test
    void rawMalformedStdinBytesReachTheStrictLoaderAndProduceTheExactError() {
        FakeIo io = new FakeIo(new byte[0],
                new byte[] {0x73, 0x74, 0x6f, 0x70, 0x20, 0x41, 0x20, (byte) 0xff, 0x0a});

        int status = Cli.run(List.of("check", "-"), io);

        assertUsage(status, io, "<stdin>: input is not valid UTF-8");
        assertEquals(0, io.fileReads);
        assertEquals(1, io.stdinReads);
        assertEquals(List.of("readStdin", "err"), io.events);
    }

    @Test
    void malformedFileBytesProduceTheExactStrictUtf8Diagnostic() {
        FakeIo io = new FakeIo(new byte[] {0x73, 0x74, 0x6f, 0x70, 0x20, 0x41, 0x20, (byte) 0xff, 0x0a},
                new byte[0]);

        int status = Cli.run(List.of("check", "bad.tt"), io);

        assertUsage(status, io, "bad.tt: input is not valid UTF-8");
        assertEquals(1, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void bomCrLfTabsCommentsAndUnterminatedLineWorkThroughTheDispatcher() {
        String text = "\uFEFF# heading\r\n"
                + "\tstop A Alpha\r\n"
                + "\tstop B Beta\r\n"
                + "service WD mon\r\n"
                + "trip R WD: A 08:00 > B 08:10";
        FakeIo io = new FakeIo(text);

        int status = Cli.run(List.of("check", "feed.tt"), io);

        assertResult(status, io, 0, "ok, 2 stops, 1 service, 1 trip\n",
                List.of("readFile", "out"));
    }

    @Test
    void feedFormatFailurePrecedesUnknownStopValidation() {
        FakeIo io = new FakeIo("stop A\n");

        int status = Cli.run(List.of("board", "Unknown", "bad.tt"), io);

        assertUsage(status, io, "bad.tt:1: expected \"stop ID NAME\"");
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void noTripsFailurePrecedesUnknownStopValidation() {
        FakeIo io = new FakeIo("stop A Alpha\nservice WD mon\n");

        int status = Cli.run(List.of("board", "Unknown", "empty.tt"), io);

        assertUsage(status, io, "empty.tt: no trips");
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void boardLooksUpStopsCaseSensitivelyAndSanitizesUnknownText() {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(List.of("board", "Missing stop", "feed.tt"), io);

        assertUsage(status, io, "unknown stop \"Missing?stop\"");
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @ParameterizedTest
    @MethodSource("tripStopErrors")
    void tripChecksFromThenToThenSameStopAfterLoading(List<String> argv, String message) {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(argv, io);

        assertUsage(status, io, message);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    private static Stream<Arguments> tripStopErrors() {
        return Stream.of(
                Arguments.of(List.of("trip", "missing", "also-missing", "feed.tt"),
                        "unknown stop \"missing\""),
                Arguments.of(List.of("trip", "A", "missing", "feed.tt"),
                        "unknown stop \"missing\""),
                Arguments.of(List.of("trip", "a", "B", "feed.tt"),
                        "unknown stop \"a\""),
                Arguments.of(List.of("trip", "A", "A", "feed.tt"),
                        "FROM and TO are the same stop"));
    }

    @Test
    void usageFailureLeavesStdoutEmptyAndWritesExactlyOneDiagnostic() {
        FakeIo io = new FakeIo(SIMPLE);

        int status = Cli.run(List.of("trip", "A", "A", "feed.tt"), io);

        assertUsage(status, io, "FROM and TO are the same stop");
    }

    private static void assertUsageWithoutRead(int status, FakeIo io, String message) {
        assertUsage(status, io, message);
        assertEquals(0, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertNull(io.requestedPath);
        assertEquals(List.of("err"), io.events);
    }

    private static void assertUsage(int status, FakeIo io, String message) {
        assertEquals(2, status);
        assertEquals("", io.outText);
        assertEquals("stoptable: " + message + "\n", io.errText);
        assertEquals(0, io.outCalls);
        assertEquals(1, io.errCalls);
        assertTrue(io.errText.endsWith("\n"));
        assertFalse(io.errText.contains("\r"));
    }

    private static void assertResult(
            int status, FakeIo io, int expectedStatus, String expectedOutput, List<String> expectedEvents) {
        assertEquals(expectedStatus, status);
        assertEquals(expectedOutput, io.outText);
        assertEquals("", io.errText);
        assertEquals(1, io.outCalls);
        assertEquals(0, io.errCalls);
        assertEquals(expectedEvents, io.events);
        assertTrue(io.outText.endsWith("\n"));
        assertFalse(io.outText.contains("\r"));
    }

    private static final class FakeIo implements Io {
        private final byte[] fileBytes;
        private final byte[] stdinBytes;
        private final List<String> events = new ArrayList<>();
        private int fileReads;
        private int stdinReads;
        private String requestedPath;
        private IOException fileFailure;
        private InvalidPathException filePathFailure;
        private IOException stdinFailure;
        private int outCalls;
        private String outText = "";
        private int errCalls;
        private String errText = "";

        private FakeIo(String fileText) {
            this(fileText.getBytes(StandardCharsets.UTF_8), new byte[0]);
        }

        private FakeIo(byte[] fileBytes, byte[] stdinBytes) {
            this.fileBytes = fileBytes;
            this.stdinBytes = stdinBytes;
        }

        @Override
        public byte[] readFile(String path) throws IOException {
            fileReads++;
            requestedPath = path;
            events.add("readFile");
            if (fileFailure != null) {
                throw fileFailure;
            }
            if (filePathFailure != null) {
                throw filePathFailure;
            }
            return fileBytes;
        }

        @Override
        public byte[] readStdin() throws IOException {
            stdinReads++;
            events.add("readStdin");
            if (stdinFailure != null) {
                throw stdinFailure;
            }
            return stdinBytes;
        }

        @Override
        public void out(String text) {
            outCalls++;
            outText = text;
            events.add("out");
        }

        @Override
        public void err(String text) {
            errCalls++;
            errText = text;
            events.add("err");
        }
    }
}
