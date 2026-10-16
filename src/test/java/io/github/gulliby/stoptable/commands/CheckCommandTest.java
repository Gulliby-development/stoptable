package io.github.gulliby.stoptable.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.stoptable.Cli;
import io.github.gulliby.stoptable.format.Io;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CheckCommandTest {
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

    private static final String MESSY = "stop A Alpha\n"
            + "stop B Beta\n"
            + "stop C Charlie\n"
            + "stop D Delta\n"
            + "stop E Echo\n"
            + "stop F Foxtrot\n"
            + "service WD mon-fri\n"
            + "service WE sat,sun\n"
            + "service XX mon\n"
            + "trip 1 WD: A 08:00 > B 08:10\n"
            + "trip 1 WD: A 08:00 > B 08:10\n"
            + "trip 1 XX: A 08:00 > B 08:12\n"
            + "trip 2 WD: E 09:00 > F 09:05\n";

    private static final String SINGLE_UNUSED_SERVICE = "stop A Alpha\n"
            + "stop B Beta\n"
            + "service WD mon\n"
            + "service WE sat\n"
            + "trip R WD: A 08:00 > B 08:10\n";

    @Test
    void printsTheVillageCleanSummary() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("check", "village.tt"), io);

        assertCliResult(status, io, 0, "ok, 5 stops, 2 services, 5 trips\n", "");
        assertFileReadThenOutput(io, "village.tt");
    }

    @Test
    void printsAllSixMessyFindingsInLineAndCodeOrder() {
        FakeIo io = new FakeIo(MESSY);

        int status = Cli.run(List.of("check", "messy.tt"), io);

        assertCliResult(status, io, 1,
                "messy.tt:3: TT001 stop \"C\" is used by no trip\n"
                        + "messy.tt:4: TT001 stop \"D\" is used by no trip\n"
                        + "messy.tt:5: TT005 stop \"E\" is not connected to stop \"A\"\n"
                        + "messy.tt:8: TT002 service \"WE\" is used by no trip\n"
                        + "messy.tt:11: TT003 trip repeats line 10\n"
                        + "messy.tt:12: TT004 route 1 also leaves stop \"A\" at 08:00 on a shared day (line 10)\n"
                        + "6 findings\n",
                "");
        assertFileReadThenOutput(io, "messy.tt");
    }

    @Test
    void printsSingularServiceAndTripCounts() {
        FakeIo io = new FakeIo("stop A Alpha\n"
                + "stop B Beta\n"
                + "service WD mon\n"
                + "trip R WD: A 08:00 > B 08:10\n");

        int status = Cli.run(List.of("check", "single.tt"), io);

        assertCliResult(status, io, 0, "ok, 2 stops, 1 service, 1 trip\n", "");
        assertFileReadThenOutput(io, "single.tt");
    }

    @Test
    void printsSingularFindingForOneUnusedService() {
        FakeIo io = new FakeIo(SINGLE_UNUSED_SERVICE);

        int status = Cli.run(List.of("check", "feed.tt"), io);

        assertCliResult(status, io, 1,
                "feed.tt:4: TT002 service \"WE\" is used by no trip\n"
                        + "1 finding\n",
                "");
        assertFileReadThenOutput(io, "feed.tt");
    }

    @Test
    void normalizesTheDisplayedPathInFindingOutputButReadsTheOriginalPath() {
        FakeIo io = new FakeIo(SINGLE_UNUSED_SERVICE);
        String path = "folder\\feed.tt";

        int status = Cli.run(List.of("check", path), io);

        assertCliResult(status, io, 1,
                "folder/feed.tt:4: TT002 service \"WE\" is used by no trip\n"
                        + "1 finding\n",
                "");
        assertFileReadThenOutput(io, path);
    }

    @Test
    void reportsMissingFileBeforeReading() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("check"), io);

        assertCliResult(status, io, 2, "", "stoptable: missing FILE\n");
        assertEquals(0, io.fileReads);
        assertEquals(List.of("err"), io.events);
    }

    @Test
    void reportsSurplusArgumentsBeforeReading() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("check", "village.tt", "extra.tt"), io);

        assertCliResult(status, io, 2, "", "stoptable: too many arguments\n");
        assertEquals(0, io.fileReads);
        assertEquals(List.of("err"), io.events);
    }

    @Test
    void reportsUnknownOptionsBeforeMissingFileArity() {
        FakeIo io = new FakeIo(VILLAGE);

        int status = Cli.run(List.of("check", "--bogus"), io);

        assertCliResult(status, io, 2, "", "stoptable: unknown option \"--bogus\"\n");
        assertEquals(0, io.fileReads);
        assertEquals(List.of("err"), io.events);
    }

    @Test
    void reportsTheWorkedVillageLineTenTimeErrorExactly() {
        FakeIo io = new FakeIo("# village bus\n"
                + "stop HAR Harbor Pier\n"
                + "stop MKT Market Square\n"
                + "stop CHU Church Lane\n"
                + "stop PRK Park Gate\n"
                + "stop SCH School\n"
                + "service WD mon-fri\n"
                + "service WE sat,sun\n"
                + "\n"
                + "trip 12 WD: HAR 07:00 > MKT 06:59\n");

        int status = Cli.run(List.of("check", "village.tt"), io);

        assertCliResult(status, io, 2, "",
                "stoptable: village.tt:10: time must be later than the previous stop\n");
        assertEquals(1, io.fileReads);
        assertEquals("village.tt", io.requestedPath);
        assertEquals(List.of("readFile", "err"), io.events);
    }

    private static void assertCliResult(
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
        assertEquals(originalPath, io.requestedPath);
        assertEquals(List.of("readFile", "out"), io.events);
        assertEquals(0, io.errCalls);
    }

    private static final class FakeIo implements Io {
        private final byte[] fileBytes;
        private final List<String> events = new ArrayList<>();
        private int fileReads;
        private String requestedPath;
        private int outCalls;
        private String outText = "";
        private int errCalls;
        private String errText = "";

        private FakeIo(String fileText) {
            fileBytes = fileText.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] readFile(String path) {
            fileReads++;
            requestedPath = path;
            events.add("readFile");
            return fileBytes;
        }

        @Override
        public byte[] readStdin() {
            throw new AssertionError("check should not read standard input in these examples");
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
