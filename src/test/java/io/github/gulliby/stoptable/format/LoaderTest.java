package io.github.gulliby.stoptable.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.stoptable.core.Failure;
import io.github.gulliby.stoptable.core.Feed;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.function.Executable;

class LoaderTest {
    @ParameterizedTest
    @MethodSource("displayPaths")
    void displaysStdinAndSlashNormalizedPaths(String path, String expected) {
        assertEquals(expected, Loader.displayPath(path));
    }

    private static Stream<Arguments> displayPaths() {
        return Stream.of(
                Arguments.of("-", "<stdin>"),
                Arguments.of("C:\\feeds\\a.tt", "C:/feeds/a.tt"),
                Arguments.of("folder\\nested\\file.tt", "folder/nested/file.tt"),
                Arguments.of("feed.tt", "feed.tt"));
    }

    @ParameterizedTest
    @MethodSource("lineSplits")
    void splitsLinesWithOnlyTheSpecifiedBomAndLineEndingRules(String text, List<String> expected) {
        assertEquals(expected, Loader.splitLines(text));
    }

    private static Stream<Arguments> lineSplits() {
        return Stream.of(
                Arguments.of("", List.of()),
                Arguments.of("\uFEFF", List.of()),
                Arguments.of("a\n", List.of("a")),
                Arguments.of("a\n\n", List.of("a", "")),
                Arguments.of("\n", List.of("")),
                Arguments.of("a\nb", List.of("a", "b")),
                Arguments.of("a\r\nb", List.of("a", "b")),
                Arguments.of("a\rb", List.of("a\rb")),
                Arguments.of("a\r\r\n", List.of("a\r")),
                Arguments.of("\uFEFFa\n", List.of("a")),
                Arguments.of("\uFEFF\uFEFFa", List.of("\uFEFFa")),
                Arguments.of("a\n\uFEFFb", List.of("a", "\uFEFFb")),
                Arguments.of("unterminated", List.of("unterminated")));
    }

    @Test
    void readTextUsesOriginalFilePathAndReturnsDecodedText() {
        FakeIo io = new FakeIo("Harbor café".getBytes(StandardCharsets.UTF_8), new byte[0]);

        Loader.Source source = Loader.readText(io, "C:\\feeds\\a.tt");

        assertEquals(new Loader.Source("C:/feeds/a.tt", "Harbor café"), source);
        assertEquals(1, io.fileReads);
        assertEquals("C:\\feeds\\a.tt", io.requestedPath);
        assertEquals(0, io.stdinReads);
        assertNoOutput(io);
    }

    @Test
    void readTextUsesOnlyStdinForTheDashPath() {
        FakeIo io = new FakeIo("unused".getBytes(StandardCharsets.UTF_8),
                "stop A Alpha".getBytes(StandardCharsets.UTF_8));

        Loader.Source source = Loader.readText(io, "-");

        assertEquals(new Loader.Source("<stdin>", "stop A Alpha"), source);
        assertEquals(0, io.fileReads);
        assertEquals(1, io.stdinReads);
        assertEquals(null, io.requestedPath);
        assertNoOutput(io);
    }

    @Test
    void readTextPreservesTheBomAndDecodesUtf8BeforeLineSplitting() {
        String text = "\uFEFFstop A Café\r\n";
        FakeIo io = new FakeIo(text.getBytes(StandardCharsets.UTF_8), new byte[0]);

        assertEquals(new Loader.Source("feed.tt", text), Loader.readText(io, "feed.tt"));
        assertEquals(List.of("stop A Café"), Loader.splitLines(text));
        assertNoOutput(io);
    }

    @Test
    void rejectsTheSpecifiedMalformedStdinBytesBeforeTryingToParseThem() {
        FakeIo io = new FakeIo(new byte[0],
                new byte[] {0x73, 0x74, 0x6f, 0x70, 0x20, 0x41, 0x20, (byte) 0xff, 0x0a});

        assertUsage("<stdin>: input is not valid UTF-8", () -> Loader.readText(io, "-"));

        assertEquals(0, io.fileReads);
        assertEquals(1, io.stdinReads);
        assertNoOutput(io);
    }

    @ParameterizedTest
    @MethodSource("malformedUtf8")
    void rejectsMalformedUtf8BytesFromFiles(byte[] bytes) {
        FakeIo io = new FakeIo(bytes, new byte[0]);

        assertUsage("bad.tt: input is not valid UTF-8", () -> Loader.readText(io, "bad.tt"));

        assertEquals(1, io.fileReads);
        assertEquals("bad.tt", io.requestedPath);
        assertEquals(0, io.stdinReads);
        assertNoOutput(io);
    }

    private static Stream<Arguments> malformedUtf8() {
        return Stream.of(
                Arguments.of(new byte[] {(byte) 0x80}),
                Arguments.of(new byte[] {(byte) 0xc0, (byte) 0xaf}),
                Arguments.of(new byte[] {(byte) 0xe2, (byte) 0x82}),
                Arguments.of(new byte[] {(byte) 0xed, (byte) 0xa0, (byte) 0x80}),
                Arguments.of(new byte[] {(byte) 0xf4, (byte) 0x90, (byte) 0x80, (byte) 0x80}));
    }

    @Test
    void mapsInvalidFilePathsToDisplayedUsageMessages() {
        FakeIo io = new FakeIo(new byte[0], new byte[0]);
        io.filePathFailure = new InvalidPathException("invalid", "invalid character");

        assertUsage("bad.tt: cannot read the file", () -> Loader.readText(io, "bad.tt"));

        assertEquals(1, io.fileReads);
        assertEquals("bad.tt", io.requestedPath);
        assertEquals(0, io.stdinReads);
        assertNoOutput(io);
    }

    @Test
    void mapsFileAndStdinReadFailuresToDisplayedUsageMessages() {
        FakeIo fileIo = new FakeIo(new byte[0], new byte[0]);
        fileIo.fileFailure = new IOException("private detail");

        assertUsage("C:/feeds/a.tt: cannot read the file", () -> Loader.readText(fileIo, "C:\\feeds\\a.tt"));
        assertEquals(1, fileIo.fileReads);
        assertEquals(0, fileIo.stdinReads);
        assertNoOutput(fileIo);

        FakeIo stdinIo = new FakeIo(new byte[0], new byte[0]);
        stdinIo.stdinFailure = new IOException("private detail");

        assertUsage("<stdin>: cannot read the file", () -> Loader.readText(stdinIo, "-"));
        assertEquals(0, stdinIo.fileReads);
        assertEquals(1, stdinIo.stdinReads);
        assertNoOutput(stdinIo);
    }

    @Test
    void loadFeedKeepsOriginalLineNumbersAcrossBomCrLfAndAnUnterminatedLastLine() {
        String text = "\uFEFF# route feed\r\n"
                + "stop A Alpha\r\n"
                + "stop B Beta\r\n"
                + "service WD mon\r\n"
                + "trip R WD: A 08:00 > B 08:10";
        FakeIo io = new FakeIo(text.getBytes(StandardCharsets.UTF_8), new byte[0]);

        Loader.Loaded loaded = Loader.loadFeed(io, "feed.tt");

        assertEquals("feed.tt", loaded.display());
        assertEquals(List.of(
                new Feed.Stop("A", "Alpha", 2),
                new Feed.Stop("B", "Beta", 3)), loaded.feed().stops());
        assertEquals(List.of(new Feed.Service("WD", 1, 4)), loaded.feed().services());
        assertEquals(List.of(new Feed.Trip("R", "WD",
                List.of(new Feed.Call("A", 480), new Feed.Call("B", 490)), 5)), loaded.feed().trips());
        assertEquals(1, io.fileReads);
        assertNoOutput(io);
    }

    @Test
    void loadFeedTranslatesFormatErrorsUsingTheDisplayedPathAndOriginalLine() {
        String text = "# heading\r\nstop A Alpha\r\nunknown thing";
        FakeIo io = new FakeIo(text.getBytes(StandardCharsets.UTF_8), new byte[0]);

        assertUsage("folder/feed.tt:3: unknown keyword \"unknown\"",
                () -> Loader.loadFeed(io, "folder\\feed.tt"));

        assertEquals("folder\\feed.tt", io.requestedPath);
        assertEquals(1, io.fileReads);
        assertNoOutput(io);
    }

    @Test
    void loadFeedReportsDecodingFailureBeforeInvalidGrammar() {
        FakeIo io = new FakeIo(new byte[] {
                0x73, 0x74, 0x6f, 0x70, 0x20, 0x41, 0x20, (byte) 0xff, 0x0a}, new byte[0]);

        assertUsage("feed.tt: input is not valid UTF-8", () -> Loader.loadFeed(io, "feed.tt"));

        assertEquals(1, io.fileReads);
        assertNoOutput(io);
    }

    @Test
    void loadFeedRejectsAValidZeroTripFeedAfterParsing() {
        FakeIo io = new FakeIo("stop A Alpha\nservice WD mon\n".getBytes(StandardCharsets.UTF_8),
                new byte[0]);

        assertUsage("feed.tt: no trips", () -> Loader.loadFeed(io, "feed.tt"));

        assertEquals(1, io.fileReads);
        assertNoOutput(io);
    }

    @Test
    void loadFeedReportsAFormatFailureBeforeTheNoTripsFailure() {
        FakeIo io = new FakeIo("stop A\n".getBytes(StandardCharsets.UTF_8), new byte[0]);

        assertUsage("feed.tt:1: expected \"stop ID NAME\"",
                () -> Loader.loadFeed(io, "feed.tt"));

        assertEquals(1, io.fileReads);
        assertNoOutput(io);
    }

    private static void assertUsage(String expectedMessage, Executable action) {
        Failure.Usage failure = assertThrows(Failure.Usage.class, action);
        assertEquals(expectedMessage, failure.getMessage());
    }

    private static void assertNoOutput(FakeIo io) {
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    private static final class FakeIo implements Io {
        private final byte[] fileBytes;
        private final byte[] stdinBytes;
        private int fileReads;
        private int stdinReads;
        private String requestedPath;
        private IOException fileFailure;
                private InvalidPathException filePathFailure;
        private IOException stdinFailure;
        private int outCalls;
        private int errCalls;

        private FakeIo(byte[] fileBytes, byte[] stdinBytes) {
            this.fileBytes = fileBytes;
            this.stdinBytes = stdinBytes;
        }

        @Override
        public byte[] readFile(String path) throws IOException {
            fileReads++;
            requestedPath = path;
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
            if (stdinFailure != null) {
                throw stdinFailure;
            }
            return stdinBytes;
        }

        @Override
        public void out(String text) {
            outCalls++;
        }

        @Override
        public void err(String text) {
            errCalls++;
        }
    }
}
