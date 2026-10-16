package io.github.gulliby.stoptable.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.stoptable.Cli;
import io.github.gulliby.stoptable.format.Io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BoardCommandTest {
    private static final String VILLAGE = "# village bus\n"
            + "stop HAR Harbor Pier\n"
            + "stop MKT Market Square\n"
            + "stop CHU Church Lane\n"
            + "stop PRK Park Gate\n"
            + "stop SCH School\n"
            + "service WD mon-fri\n"
            + "service WE sat,sun\n"
            + "\n"
            + "trip 12 WD: HAR 07:00 > MKT 07:10 > CHU 07:18 > PRK 07:30\n"
            + "trip 12 WD: HAR 08:00 > MKT 08:10 > CHU 08:18 > PRK 08:30\n"
            + "trip 14 WD: MKT 07:12 > SCH 07:35 > PRK 07:50\n"
            + "trip 14 WD: MKT 08:15 > SCH 08:35 > PRK 08:50\n"
            + "trip 20 WE: HAR 09:00 > MKT 09:12 > PRK 09:40\n";

    private static final String ELEVEN_DEPARTURES = "stop A Alpha\n"
            + "stop B Beta\n"
            + "service WD mon-fri\n"
            + "trip T00 WD: A 00:00 > B 00:01\n"
            + "trip T01 WD: A 08:00 > B 08:01\n"
            + "trip T02 WD: A 08:00 > B 08:01\n"
            + "trip T03 WD: A 08:00 > B 08:01\n"
            + "trip T04 WD: A 08:00 > B 08:01\n"
            + "trip T05 WD: A 08:00 > B 08:01\n"
            + "trip T06 WD: A 08:00 > B 08:01\n"
            + "trip T07 WD: A 08:00 > B 08:01\n"
            + "trip T08 WD: A 08:00 > B 08:01\n"
            + "trip T09 WD: A 08:00 > B 08:01\n"
            + "trip T10 WD: A 08:00 > B 08:01\n";

    private static final String DAILY = "stop A Alpha\n"
            + "stop B Beta\n"
            + "service ALL mon-sun\n"
            + "trip R ALL: A 06:00 > B 06:10\n";

    private static final String TIED_DEPARTURES = "stop A Alpha\n"
            + "stop B First Destination\n"
            + "stop C Charlie Destination\n"
            + "stop D Second Destination\n"
            + "service WD mon\n"
            + "trip 2 WD: A 08:00 > B 08:10\n"
            + "trip 10 WD: A 08:00 > C 08:10\n"
            + "trip 2 WD: A 08:00 > D 08:10\n";

    @Test
    void villageLimitedBoardMatchesTheSuppliedOutputExactly() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("board", "MKT", "--after", "07:11", "--limit", "2", "village.tt"), io);

        assertResult(status, io, 0,
                "MKT Market Square, mon from 07:11\n"
                        + "time   route  to\n"
                        + "07:12  14     Park Gate\n"
                        + "08:10  12     Park Gate\n"
                        + "+1 more\n",
                "");
        assertFileReadThenOutput(io, "village.tt");
    }

    @Test
    void saturdaySchoolBoardPrintsTheEmptyResultAndReturnsOne() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("board", "--day", "sat", "SCH", "village.tt"), io);

        assertResult(status, io, 1, "SCH School, sat from 00:00\nno departures\n", "");
        assertFileReadThenOutput(io, "village.tt");
    }

    @Test
    void defaultsUseMondayMidnightAndTenRows() {
        FakeIo io = new FakeIo(ELEVEN_DEPARTURES);

        int status = Cli.run(List.of("board", "A", "departures.tt"), io);

        assertResult(status, io, 0,
                "A Alpha, mon from 00:00\n"
                        + "time   route  to\n"
                        + "00:00  T00    Beta\n"
                        + "08:00  T01    Beta\n"
                        + "08:00  T02    Beta\n"
                        + "08:00  T03    Beta\n"
                        + "08:00  T04    Beta\n"
                        + "08:00  T05    Beta\n"
                        + "08:00  T06    Beta\n"
                        + "08:00  T07    Beta\n"
                        + "08:00  T08    Beta\n"
                        + "08:00  T09    Beta\n"
                        + "+1 more\n",
                "");
        assertFileReadThenOutput(io, "departures.tt");
    }

    @Test
    void limitOneShowsTheFirstDepartureAndCountsAllTenRemaining() {
        FakeIo io = new FakeIo(ELEVEN_DEPARTURES);

        int status = Cli.run(List.of("board", "--limit", "1", "A", "departures.tt"), io);

        assertResult(status, io, 0,
                "A Alpha, mon from 00:00\n"
                        + "time   route  to\n"
                        + "00:00  T00    Beta\n"
                        + "+10 more\n",
                "");
        assertFileReadThenOutput(io, "departures.tt");
    }

    @Test
    void limitNinetyNineShowsAllElevenWithoutPagination() {
        FakeIo io = new FakeIo(ELEVEN_DEPARTURES);

        int status = Cli.run(List.of("board", "A", "--limit=99", "departures.tt"), io);

        assertResult(status, io, 0,
                "A Alpha, mon from 00:00\n"
                        + "time   route  to\n"
                        + "00:00  T00    Beta\n"
                        + "08:00  T01    Beta\n"
                        + "08:00  T02    Beta\n"
                        + "08:00  T03    Beta\n"
                        + "08:00  T04    Beta\n"
                        + "08:00  T05    Beta\n"
                        + "08:00  T06    Beta\n"
                        + "08:00  T07    Beta\n"
                        + "08:00  T08    Beta\n"
                        + "08:00  T09    Beta\n"
                        + "08:00  T10    Beta\n",
                "");
        assertFileReadThenOutput(io, "departures.tt");
    }

    @Test
    void exactLimitIncludesDepartureAtAfterAndHasNoMoreLine() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("board", "MKT", "village.tt", "--after=07:12", "--limit=3"), io);

        assertResult(status, io, 0,
                "MKT Market Square, mon from 07:12\n"
                        + "time   route  to\n"
                        + "07:12  14     Park Gate\n"
                        + "08:10  12     Park Gate\n"
                        + "08:15  14     Park Gate\n",
                "");
        assertFileReadThenOutput(io, "village.tt");
    }

    @Test
    void afterFiltersOutEarlierDeparturesAndPreservesLaterRows() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("board", "MKT", "--after", "08:11", "village.tt"), io);

        assertResult(status, io, 0,
                "MKT Market Square, mon from 08:11\n"
                        + "time   route  to\n"
                        + "08:15  14     Park Gate\n",
                "");
        assertFileReadThenOutput(io, "village.tt");
    }

    @Test
    void sortsEqualTimesByAsciiRouteThenTripPositionAndRendersNamesWithoutTrailingSpaces() {
        FakeIo io = new FakeIo(TIED_DEPARTURES);

        int status = Cli.run(List.of("board", "A", "ties.tt"), io);

        assertResult(status, io, 0,
                "A Alpha, mon from 00:00\n"
                        + "time   route  to\n"
                        + "08:00  10     Charlie Destination\n"
                        + "08:00  2      First Destination\n"
                        + "08:00  2      Second Destination\n",
                "");
        assertFalse(io.outText.matches("(?s).*\\h+\\n.*"));
        assertFileReadThenOutput(io, "ties.tt");
    }

    @ParameterizedTest
    @MethodSource("validDays")
    void acceptsEachDayChoiceAndDisplaysItsDayName(String day) {
        FakeIo io = new FakeIo(DAILY);

        int status = Cli.run(List.of("board", "A", "--day", day, "daily.tt"), io);

        assertResult(status, io, 0,
                "A Alpha, " + day + " from 00:00\n"
                        + "time   route  to\n"
                        + "06:00  R      Beta\n",
                "");
        assertFileReadThenOutput(io, "daily.tt");
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
    void terminalStopHasNoDeparture() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("board", "PRK", "village.tt"), io);

        assertResult(status, io, 1, "PRK Park Gate, mon from 00:00\nno departures\n", "");
        assertFileReadThenOutput(io, "village.tt");
    }

    @Test
    void afterLastDepartureHasNoRows() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("board", "HAR", "--after", "23:59", "village.tt"), io);

        assertResult(status, io, 1, "HAR Harbor Pier, mon from 23:59\nno departures\n", "");
        assertFileReadThenOutput(io, "village.tt");
    }

    @Test
    void missingStopIsReportedBeforeMissingFileAndDoesNotRead() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("board"), io);

        assertResult(status, io, 2, "", "stoptable: missing STOP\n");
        assertEquals(0, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals(List.of("err"), io.events);
    }

    @Test
    void missingFileIsReportedBeforeReading() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("board", "A"), io);

        assertResult(status, io, 2, "", "stoptable: missing FILE\n");
        assertEquals(0, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals(List.of("err"), io.events);
    }

    @Test
    void tooManyArgumentsAreReportedBeforeReading() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("board", "A", "feed.tt", "extra"), io);

        assertResult(status, io, 2, "", "stoptable: too many arguments\n");
        assertEquals(0, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals(List.of("err"), io.events);
    }

    @Test
    void stopLookupIsCaseSensitiveAndHappensAfterLoading() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("board", "mkt", "village.tt"), io);

        assertResult(status, io, 2, "", "stoptable: unknown stop \"mkt\"\n");
        assertEquals(1, io.fileReads);
        assertEquals("village.tt", io.requestedPath);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void feedFormatErrorWinsOverAnUnknownStop() {
        FakeIo io = new FakeIo("stop A Alpha\n"
                + "stop B Beta\n"
                + "service WD mon\n"
                + "trip R WD: A nope > B 08:00\n");

        int status = Cli.run(List.of("board", "MISSING", "broken.tt"), io);

        assertResult(status, io, 2, "", "stoptable: broken.tt:4: invalid time \"nope\"\n");
        assertEquals(1, io.fileReads);
        assertEquals("broken.tt", io.requestedPath);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void readFailureWinsOverAnUnknownStopAndIsSanitizedByLoader() {
        FakeIo io = new FakeIo(VILLAGE);
        io.fileFailure = new IOException("private detail");

        int status = Cli.run(List.of("board", "MISSING", "feed.tt"), io);

        assertResult(status, io, 2, "", "stoptable: feed.tt: cannot read the file\n");
        assertEquals(1, io.fileReads);
        assertEquals("feed.tt", io.requestedPath);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void dashFileUsesStandardInputWithoutAttemptingAFileRead() {
        FakeIo io = new FakeIo("unused", DAILY);

        int status = Cli.run(List.of("board", "A", "-"), io);

        assertResult(status, io, 0,
                "A Alpha, mon from 00:00\n"
                        + "time   route  to\n"
                        + "06:00  R      Beta\n",
                "");
        assertEquals(0, io.fileReads);
        assertEquals(1, io.stdinReads);
        assertEquals(List.of("readStdin", "out"), io.events);
    }

    @Test
    void invalidDayIsReportedBeforeMissingStopArity() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("board", "--day", "Funday"), io);

        assertResult(status, io, 2, "",
                "stoptable: invalid --day \"Funday\" (expected mon, tue, wed, thu, fri, sat or sun)\n");
        assertEquals(0, io.fileReads);
        assertEquals(List.of("err"), io.events);
    }

    @Test
    void invalidTimeIsReportedBeforeMissingStopArity() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("board", "--after", "24:00"), io);

        assertResult(status, io, 2, "",
                "stoptable: invalid --after \"24:00\" (expected a time from 00:00 to 23:59)\n");
        assertEquals(0, io.fileReads);
        assertEquals(List.of("err"), io.events);
    }

    @Test
    void lowerLimitBoundaryIsAcceptedAndZeroIsRejectedBeforeArity() {
        FakeIo validIo = new FakeIo(ELEVEN_DEPARTURES);

        int validStatus = Cli.run(List.of("board", "A", "--limit", "1", "departures.tt"), validIo);

        assertResult(validStatus, validIo, 0,
                "A Alpha, mon from 00:00\n"
                        + "time   route  to\n"
                        + "00:00  T00    Beta\n"
                        + "+10 more\n",
                "");
        assertFileReadThenOutput(validIo, "departures.tt");

        FakeIo invalidIo = new FakeIo(VILLAGE);

        int invalidStatus = Cli.run(List.of("board", "--limit", "0"), invalidIo);

        assertResult(invalidStatus, invalidIo, 2, "",
                "stoptable: invalid --limit \"0\" (expected an integer from 1 to 99)\n");
        assertEquals(0, invalidIo.fileReads);
        assertEquals(List.of("err"), invalidIo.events);
    }

    @Test
    void upperLimitBoundaryIsAcceptedAndOneHundredIsRejected() {
        FakeIo validIo = new FakeIo(ELEVEN_DEPARTURES);

        int validStatus = Cli.run(List.of("board", "A", "--limit", "99", "departures.tt"), validIo);

        assertResult(validStatus, validIo, 0,
                "A Alpha, mon from 00:00\n"
                        + "time   route  to\n"
                        + "00:00  T00    Beta\n"
                        + "08:00  T01    Beta\n"
                        + "08:00  T02    Beta\n"
                        + "08:00  T03    Beta\n"
                        + "08:00  T04    Beta\n"
                        + "08:00  T05    Beta\n"
                        + "08:00  T06    Beta\n"
                        + "08:00  T07    Beta\n"
                        + "08:00  T08    Beta\n"
                        + "08:00  T09    Beta\n"
                        + "08:00  T10    Beta\n",
                "");
        assertFileReadThenOutput(validIo, "departures.tt");

        FakeIo invalidIo = new FakeIo(VILLAGE);

        int invalidStatus = Cli.run(List.of("board", "--limit", "100"), invalidIo);

        assertResult(invalidStatus, invalidIo, 2, "",
                "stoptable: invalid --limit \"100\" (expected an integer from 1 to 99)\n");
        assertEquals(0, invalidIo.fileReads);
        assertEquals(List.of("err"), invalidIo.events);
    }

    @Test
    void unknownOptionAndMissingOptionValuePrecedeArityAndFileReads() {
        FakeIo unknownIo = new FakeIo(VILLAGE);

        int unknownStatus = Cli.run(List.of("board", "--bogus"), unknownIo);

        assertResult(unknownStatus, unknownIo, 2, "", "stoptable: unknown option \"--bogus\"\n");
        assertEquals(0, unknownIo.fileReads);
        assertEquals(List.of("err"), unknownIo.events);

        FakeIo missingValueIo = new FakeIo(VILLAGE);

        int missingValueStatus = Cli.run(List.of("board", "--day"), missingValueIo);

        assertResult(missingValueStatus, missingValueIo, 2, "", "stoptable: --day needs a value\n");
        assertEquals(0, missingValueIo.fileReads);
        assertEquals(List.of("err"), missingValueIo.events);
    }

    private static void assertResult(
            int actualStatus, FakeIo io, int expectedStatus, String expectedOut, String expectedErr) {
        assertEquals(expectedStatus, actualStatus);
        assertEquals(expectedOut, io.outText);
        assertEquals(expectedErr, io.errText);
        assertFalse(io.outText.contains("\r"));
        assertFalse(io.errText.contains("\r"));

        if (expectedOut.isEmpty()) {
            assertEquals(0, io.outCalls);
        } else {
            assertEquals(1, io.outCalls);
            assertTrue(io.outText.endsWith("\n"));
        }
        if (expectedErr.isEmpty()) {
            assertEquals(0, io.errCalls);
        } else {
            assertEquals(1, io.errCalls);
            assertTrue(io.errText.endsWith("\n"));
        }
    }

    private static void assertFileReadThenOutput(FakeIo io, String originalPath) {
        assertEquals(1, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals(originalPath, io.requestedPath);
        assertEquals(List.of("readFile", "out"), io.events);
        assertEquals(0, io.errCalls);
    }

    private static final class FakeIo implements Io {
        private final byte[] fileBytes;
        private final byte[] stdinBytes;
        private final List<String> events = new ArrayList<>();
        private int fileReads;
        private int stdinReads;
        private String requestedPath;
        private IOException fileFailure;
        private int outCalls;
        private String outText = "";
        private int errCalls;
        private String errText = "";

        private FakeIo(String fileText) {
            this(fileText, "");
        }

        private FakeIo(String fileText, String stdinText) {
            fileBytes = fileText.getBytes(StandardCharsets.UTF_8);
            stdinBytes = stdinText.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] readFile(String path) throws IOException {
            fileReads++;
            requestedPath = path;
            events.add("readFile");
            if (fileFailure != null) {
                throw fileFailure;
            }
            return fileBytes;
        }

        @Override
        public byte[] readStdin() throws IOException {
            stdinReads++;
            events.add("readStdin");
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
