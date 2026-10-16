package io.github.gulliby.stoptable.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.stoptable.Cli;
import io.github.gulliby.stoptable.format.Io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class TripCommandTest {
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

    private static final String TIE = "stop A Start\n"
            + "stop M Mid\n"
            + "stop Z End\n"
            + "service WD mon-fri\n"
            + "trip 7 WD: A 07:10 > Z 07:30\n"
            + "trip 5 WD: A 07:00 > M 07:05\n"
            + "trip 6 WD: M 07:08 > Z 07:30\n";

    private static final String STAYING_ABOARD = "stop A Alpha\n"
            + "stop M Mid\n"
            + "stop Z Zulu\n"
            + "service ALL mon-sun\n"
            + "trip 10 ALL: A 06:00 > M 06:10 > Z 06:20\n";

    private static final String THREE_LEGS = "stop A Alpha\n"
            + "stop B Beta\n"
            + "stop C Charlie\n"
            + "stop D Delta\n"
            + "service WD mon\n"
            + "trip 1 WD: A 07:00 > B 07:05\n"
            + "trip 2 WD: B 07:08 > C 07:13\n"
            + "trip 3 WD: C 07:15 > D 07:20\n";

    @Test
    void villageJourneyWithZeroTransferMatchesTheSuppliedOutputExactly() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("trip", "HAR", "SCH", "--after", "06:30", "village.tt"), io);

        assertResult(status, io, 0,
                "HAR to SCH, mon from 06:30, transfer 0 min\n"
                        + "leg 1: route 12  HAR 07:00 > MKT 07:10\n"
                        + "leg 2: route 14  MKT 07:12 > SCH 07:35\n"
                        + "arrive 07:35, 65 min, 1 change\n",
                "");
        assertFileReadThenOutput(io, "village.tt");
    }

    @Test
    void threeMinuteTransferUsesTheLaterVillageBusAndExactDuration() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of(
                "trip", "HAR", "SCH", "--after", "06:30", "--transfer", "3", "village.tt"), io);

        assertResult(status, io, 0,
                "HAR to SCH, mon from 06:30, transfer 3 min\n"
                        + "leg 1: route 12  HAR 07:00 > MKT 07:10\n"
                        + "leg 2: route 14  MKT 08:15 > SCH 08:35\n"
                        + "arrive 08:35, 125 min, 1 change\n",
                "");
        assertFileReadThenOutput(io, "village.tt");
    }

    @Test
    void reverseVillageJourneyPrintsNoJourneyAndReturnsOne() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("trip", "SCH", "HAR", "village.tt"), io);

        assertResult(status, io, 1,
                "SCH to HAR, mon from 00:00, transfer 0 min\nno journey\n",
                "");
        assertFileReadThenOutput(io, "village.tt");
    }

    @Test
    void equalArrivalKeepsTheFirstScannedTwoLegJourney() {
        FakeIo io = new FakeIo(TIE);

        int status = Cli.run(List.of("trip", "A", "Z", "--after", "06:30", "tie.tt"), io);

        assertResult(status, io, 0,
                "A to Z, mon from 06:30, transfer 0 min\n"
                        + "leg 1: route 5  A 07:00 > M 07:05\n"
                        + "leg 2: route 6  M 07:08 > Z 07:30\n"
                        + "arrive 07:30, 60 min, 1 change\n",
                "");
        assertFileReadThenOutput(io, "tie.tt");
    }

    @Test
    void defaultDayAndAfterAndMaximumTransferAllowStayingAboardWithoutAChange() {
        FakeIo io = new FakeIo(STAYING_ABOARD);

        int status = Cli.run(List.of("trip", "A", "Z", "--transfer=120", "daily.tt"), io);

        assertResult(status, io, 0,
                "A to Z, mon from 00:00, transfer 120 min\n"
                        + "leg 1: route 10  A 06:00 > Z 06:20\n"
                        + "arrive 06:20, 380 min, 0 changes\n",
                "");
        assertFileReadThenOutput(io, "daily.tt");
    }

    @Test
    void threeLegJourneyNumbersEveryLegAndUsesPluralChanges() {
        FakeIo io = new FakeIo(THREE_LEGS);

        int status = Cli.run(List.of("trip", "A", "D", "--after=07:00", "three.tt"), io);

        assertResult(status, io, 0,
                "A to D, mon from 07:00, transfer 0 min\n"
                        + "leg 1: route 1  A 07:00 > B 07:05\n"
                        + "leg 2: route 2  B 07:08 > C 07:13\n"
                        + "leg 3: route 3  C 07:15 > D 07:20\n"
                        + "arrive 07:20, 20 min, 2 changes\n",
                "");
        assertFileReadThenOutput(io, "three.tt");
    }

    @Test
    void dashFileReadsStdinAndNeverAttemptsAFileRead() {
        FakeIo io = new FakeIo("unused", STAYING_ABOARD);

        int status = Cli.run(List.of("trip", "A", "Z", "-"), io);

        assertResult(status, io, 0,
                "A to Z, mon from 00:00, transfer 0 min\n"
                        + "leg 1: route 10  A 06:00 > Z 06:20\n"
                        + "arrive 06:20, 380 min, 0 changes\n",
                "");
        assertEquals(0, io.fileReads);
        assertEquals(1, io.stdinReads);
        assertEquals(List.of("readStdin", "out"), io.events);
    }

    @Test
    void missingFromIsReportedBeforeAllOtherArityChecks() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("trip"), io);

        assertUsageResult(status, io, "missing FROM");
        assertNoReads(io);
    }

    @Test
    void missingToIsReportedBeforeFileAndDoesNotRead() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("trip", "HAR"), io);

        assertUsageResult(status, io, "missing TO");
        assertNoReads(io);
    }

    @Test
    void missingFileIsReportedBeforeReading() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("trip", "HAR", "SCH"), io);

        assertUsageResult(status, io, "missing FILE");
        assertNoReads(io);
    }

    @Test
    void tooManyArgumentsAreReportedBeforeReading() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("trip", "HAR", "SCH", "village.tt", "extra"), io);

        assertUsageResult(status, io, "too many arguments");
        assertNoReads(io);
    }

    @Test
    void unknownFromIsReportedBeforeUnknownToAfterLoading() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("trip", "MISSING", "ALSO_MISSING", "village.tt"), io);

        assertUsageResult(status, io, "unknown stop \"MISSING\"");
        assertEquals(1, io.fileReads);
        assertEquals("village.tt", io.requestedPath);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void unknownToIsReportedAfterLoadingAndAfterKnownFrom() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("trip", "HAR", "MISSING", "village.tt"), io);

        assertUsageResult(status, io, "unknown stop \"MISSING\"");
        assertEquals(1, io.fileReads);
        assertEquals("village.tt", io.requestedPath);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void sameKnownStopsAreRejectedOnlyAfterTheFeedIsLoaded() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("trip", "HAR", "HAR", "village.tt"), io);

        assertUsageResult(status, io, "FROM and TO are the same stop");
        assertEquals(1, io.fileReads);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void formatFailureWinsOverUnknownStops() {
        FakeIo io = new FakeIo("stop A Alpha\n"
                + "stop B Beta\n"
                + "service WD mon\n"
                + "trip R WD: A nope > B 08:00\n");

        int status = Cli.run(List.of("trip", "MISSING", "ALSO_MISSING", "broken.tt"), io);

        assertUsageResult(status, io, "broken.tt:4: invalid time \"nope\"");
        assertEquals(1, io.fileReads);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void noTripsFailureWinsOverUnknownStops() {
        FakeIo io = new FakeIo("stop A Alpha\nservice WD mon\n");

        int status = Cli.run(List.of("trip", "MISSING", "ALSO_MISSING", "empty.tt"), io);

        assertUsageResult(status, io, "empty.tt: no trips");
        assertEquals(1, io.fileReads);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void readFailureWinsOverUnknownStopsAndDoesNotExposeTheIoDetail() {
        FakeIo io = new FakeIo(VILLAGE);
        io.fileFailure = new IOException("private detail");

        int status = Cli.run(List.of("trip", "MISSING", "ALSO_MISSING", "folder\\village.tt"), io);

        assertUsageResult(status, io, "folder/village.tt: cannot read the file");
        assertEquals(1, io.fileReads);
        assertEquals("folder\\village.tt", io.requestedPath);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void invalidPathIsConvertedToTheNormalizedReadDiagnostic() {
        FakeIo io = new FakeIo(VILLAGE);
        io.filePathFailure = new InvalidPathException("bad\\path", "invalid character");

        int status = Cli.run(List.of("trip", "HAR", "SCH", "bad\\path.tt"), io);

        assertUsageResult(status, io, "bad/path.tt: cannot read the file");
        assertEquals(1, io.fileReads);
        assertEquals("bad\\path.tt", io.requestedPath);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    @Test
    void malformedUtf8OnStdinIsReportedWithoutParsingOrStdout() {
        FakeIo io = new FakeIo(new byte[0],
                new byte[] {0x73, 0x74, 0x6f, 0x70, 0x20, 0x41, 0x20, (byte) 0xff, 0x0a});

        int status = Cli.run(List.of("trip", "A", "B", "-"), io);

        assertUsageResult(status, io, "<stdin>: input is not valid UTF-8");
        assertEquals(0, io.fileReads);
        assertEquals(1, io.stdinReads);
        assertEquals(List.of("readStdin", "err"), io.events);
    }

    @Test
    void invalidTransferAboveMaximumIsReportedBeforeArityAndLoading() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("trip", "--transfer", "121"), io);

        assertUsageResult(status, io,
                "invalid --transfer \"121\" (expected an integer from 0 to 120)");
        assertNoReads(io);
    }

    @Test
    void negativeTransferValueIsConsumedThenRejectedByValueValidation() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("trip", "--transfer", "-1"), io);

        assertUsageResult(status, io,
                "invalid --transfer \"-1\" (expected an integer from 0 to 120)");
        assertNoReads(io);
    }

    @Test
    void invalidAfterTimeIsReportedBeforeMissingFrom() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("trip", "--after", "7:11"), io);

        assertUsageResult(status, io,
                "invalid --after \"7:11\" (expected a time from 00:00 to 23:59)");
        assertNoReads(io);
    }

    @Test
    void unknownOptionAndMissingOptionValuePrecedeArityAndFileReads() {
        FakeIo unknownIo = new FakeIo(VILLAGE);

        int unknownStatus = Cli.run(List.of("trip", "--bogus"), unknownIo);

        assertUsageResult(unknownStatus, unknownIo, "unknown option \"--bogus\"");
        assertNoReads(unknownIo);

        FakeIo missingValueIo = new FakeIo(VILLAGE);

        int missingValueStatus = Cli.run(List.of("trip", "--transfer"), missingValueIo);

        assertUsageResult(missingValueStatus, missingValueIo, "--transfer needs a value");
        assertNoReads(missingValueIo);
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

    private static void assertUsageResult(int actualStatus, FakeIo io, String message) {
        assertResult(actualStatus, io, 2, "", "stoptable: " + message + "\n");
        assertEquals(0, io.outCalls);
        assertEquals(1, io.errCalls);
    }

    private static void assertNoReads(FakeIo io) {
        assertEquals(0, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals(List.of("err"), io.events);
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
        private InvalidPathException filePathFailure;
        private int outCalls;
        private String outText = "";
        private int errCalls;
        private String errText = "";

        private FakeIo(String fileText) {
            this(fileText, "");
        }

        private FakeIo(String fileText, String stdinText) {
            this(fileText.getBytes(StandardCharsets.UTF_8), stdinText.getBytes(StandardCharsets.UTF_8));
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
