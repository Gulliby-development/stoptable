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

class ExportCommandTest {
    private static final String TIE = "stop A Start\n"
            + "stop M Mid\n"
            + "stop Z End\n"
            + "service WD mon-fri\n"
            + "trip 7 WD: A 07:10 > Z 07:30\n"
            + "trip 5 WD: A 07:00 > M 07:05\n"
            + "trip 6 WD: M 07:08 > Z 07:30\n";

    private static final String ORDERED = "stop C Charlie\n"
            + "stop A Alpha\n"
            + "stop B Beta\n"
            + "stop X Unused\n"
            + "service MO mon\n"
            + "service SA sat\n"
            + "trip z MO: A 07:00 > B 07:10\n"
            + "trip A SA: A 08:00 > B 08:10\n"
            + "trip Z MO: A 09:00 > B 09:10\n"
            + "trip a SA: A 10:00 > B 10:10\n"
            + "trip A SA: A 11:00 > B 11:10\n"
            + "trip A SA: C 08:00 > A 08:10\n";

    @Test
    void defaultDotMatchesTheSuppliedTieDiagramLiterally() {
        FakeIo io = new FakeIo(TIE);

        int status = Cli.run(List.of("export", "tie.tt"), io);

        assertOutput(status, io, "tie.tt",
                "digraph stoptable {\n"
                        + "  rankdir=LR;\n"
                        + "  \"A\" [label=\"Start\"];\n"
                        + "  \"M\" [label=\"Mid\"];\n"
                        + "  \"Z\" [label=\"End\"];\n"
                        + "  \"A\" -> \"M\" [label=\"5\"];\n"
                        + "  \"A\" -> \"Z\" [label=\"7\"];\n"
                        + "  \"M\" -> \"Z\" [label=\"6\"];\n"
                        + "}\n");
    }

    @Test
    void explicitDotFormatMatchesTheSameCompleteDiagram() {
        FakeIo io = new FakeIo(TIE);

        int status = Cli.run(List.of("export", "tie.tt", "--format=dot"), io);

        assertOutput(status, io, "tie.tt",
                "digraph stoptable {\n"
                        + "  rankdir=LR;\n"
                        + "  \"A\" [label=\"Start\"];\n"
                        + "  \"M\" [label=\"Mid\"];\n"
                        + "  \"Z\" [label=\"End\"];\n"
                        + "  \"A\" -> \"M\" [label=\"5\"];\n"
                        + "  \"A\" -> \"Z\" [label=\"7\"];\n"
                        + "  \"M\" -> \"Z\" [label=\"6\"];\n"
                        + "}\n");
    }

    @Test
    void mermaidFormatMatchesEverySuppliedTieNodeAndEdge() {
        FakeIo io = new FakeIo(TIE);

        int status = Cli.run(List.of("export", "--format=mermaid", "tie.tt"), io);

        assertOutput(status, io, "tie.tt",
                "graph LR\n"
                        + "  A[\"Start\"]\n"
                        + "  M[\"Mid\"]\n"
                        + "  Z[\"End\"]\n"
                        + "  A -->|5| M\n"
                        + "  A -->|7| Z\n"
                        + "  M -->|6| Z\n");
    }

    @Test
    void routesAreDeduplicatedAsciiSortedAndEdgesFollowStopDeclarationOrderAcrossServices() {
        FakeIo io = new FakeIo(ORDERED);

        int status = Cli.run(List.of("export", "ordered.tt"), io);

        assertOutput(status, io, "ordered.tt",
                "digraph stoptable {\n"
                        + "  rankdir=LR;\n"
                        + "  \"C\" [label=\"Charlie\"];\n"
                        + "  \"A\" [label=\"Alpha\"];\n"
                        + "  \"B\" [label=\"Beta\"];\n"
                        + "  \"C\" -> \"A\" [label=\"A\"];\n"
                        + "  \"A\" -> \"B\" [label=\"A,Z,a,z\"];\n"
                        + "}\n");
    }

    @Test
    void lastRepeatedFormatOptionWinsAndCanAppearAfterTheFile() {
        FakeIo io = new FakeIo(TIE);

        int status = Cli.run(List.of(
                "export", "--format=mermaid", "tie.tt", "--format", "dot"), io);

        assertOutput(status, io, "tie.tt",
                "digraph stoptable {\n"
                        + "  rankdir=LR;\n"
                        + "  \"A\" [label=\"Start\"];\n"
                        + "  \"M\" [label=\"Mid\"];\n"
                        + "  \"Z\" [label=\"End\"];\n"
                        + "  \"A\" -> \"M\" [label=\"5\"];\n"
                        + "  \"A\" -> \"Z\" [label=\"7\"];\n"
                        + "  \"M\" -> \"Z\" [label=\"6\"];\n"
                        + "}\n");
    }

    @Test
    void missingFileIsReportedWithoutReadingOrWritingStdout() {
        FakeIo io = new FakeIo(TIE);

        int status = Cli.run(List.of("export"), io);

        assertUsageWithoutRead(status, io, "missing FILE");
    }

    @Test
    void excessArgumentsAreReportedBeforeAnyFileRead() {
        FakeIo io = new FakeIo(TIE);

        int status = Cli.run(List.of("export", "tie.tt", "extra.tt"), io);

        assertUsageWithoutRead(status, io, "too many arguments");
    }

    @Test
    void invalidFormatIsReportedBeforeTheMissingFileArityError() {
        FakeIo io = new FakeIo(TIE);

        int status = Cli.run(List.of("export", "--format", "png"), io);

        assertUsageWithoutRead(status, io,
                "invalid --format \"png\" (expected dot or mermaid)");
    }

    @Test
    void optionValueStartingWithDashIsConsumedAndValidatedBeforeArity() {
        FakeIo io = new FakeIo(TIE);

        int status = Cli.run(List.of("export", "--format", "--not-an-option"), io);

        assertUsageWithoutRead(status, io,
                "invalid --format \"--not-an-option\" (expected dot or mermaid)");
    }

    @Test
    void invalidFormatAfterFirstEqualsIsReportedBeforeArity() {
        FakeIo io = new FakeIo(TIE);

        int status = Cli.run(List.of("export", "--format=dot=mermaid"), io);

        assertUsageWithoutRead(status, io,
                "invalid --format \"dot=mermaid\" (expected dot or mermaid)");
    }

    @Test
    void unknownOptionIsReportedBeforeMissingFileAndDoesNotRead() {
        FakeIo io = new FakeIo(TIE);

        int status = Cli.run(List.of("export", "--unknown"), io);

        assertUsageWithoutRead(status, io, "unknown option \"--unknown\"");
    }

    @Test
    void missingFormatValueIsReportedBeforeMissingFileAndDoesNotRead() {
        FakeIo io = new FakeIo(TIE);

        int status = Cli.run(List.of("export", "--format"), io);

        assertUsageWithoutRead(status, io, "--format needs a value");
    }

    private static void assertOutput(int status, FakeIo io, String expectedPath, String expectedOutput) {
        assertEquals(0, status);
        assertEquals(expectedOutput, io.outText);
        assertEquals("", io.errText);
        assertEquals(1, io.outCalls);
        assertEquals(0, io.errCalls);
        assertEquals(1, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals(expectedPath, io.requestedPath);
        assertEquals(List.of("readFile", "out"), io.events);
        assertTrue(io.outText.endsWith("\n"));
        assertFalse(io.outText.contains("\r"));
        assertFalse(io.errText.contains("\r"));
    }

    private static void assertUsageWithoutRead(int status, FakeIo io, String message) {
        assertEquals(2, status);
        assertEquals("", io.outText);
        assertEquals("stoptable: " + message + "\n", io.errText);
        assertEquals(0, io.outCalls);
        assertEquals(1, io.errCalls);
        assertEquals(0, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals(null, io.requestedPath);
        assertEquals(List.of("err"), io.events);
        assertFalse(io.outText.contains("\r"));
        assertTrue(io.errText.endsWith("\n"));
        assertFalse(io.errText.contains("\r"));
    }

    private static final class FakeIo implements Io {
        private final byte[] fileBytes;
        private final List<String> events = new ArrayList<>();
        private int fileReads;
        private int stdinReads;
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
            stdinReads++;
            events.add("readStdin");
            return new byte[0];
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
