package io.github.gulliby.stoptable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.stoptable.format.Io;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class MainTest {
    private static final Pattern TEXT_BLOCK = Pattern.compile("(?s)```text\\n(.*?)\\n```");
    private static final byte[] INVALID_STDIN = {
            0x73, 0x74, 0x6f, 0x70, 0x20, 0x41, 0x20, (byte) 0xff, 0x0a
    };

    @Test
    void READMEFeedAndJourneyOutputRunThroughTheCli() throws IOException {
        String markdown = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        String feed = textBlockBetween(markdown,
                "The example feed below is used by the journey example; save this as village.tt.",
                "## Commands");
        String expectedOutput = textBlockBetween(markdown, "The command prints:", "## Exit codes");
        FakeIo io = new FakeIo(feed.getBytes(StandardCharsets.UTF_8));

        int status = Cli.run(List.of("trip", "HAR", "SCH", "--after", "06:30", "village.tt"), io);

        assertEquals(0, status);
        assertEquals("village.tt", io.readPath);
        assertEquals(1, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals(1, io.outCalls);
        assertEquals(expectedOutput + "\n", io.outText);
        assertEquals(0, io.errCalls);
        assertEquals("", io.errText);
    }

    @Test
    void malformedUtf8OnRealStdinProducesExactBytesAndExitStatus() throws IOException, InterruptedException {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        if (!Files.isExecutable(java)) {
            java = java.resolveSibling("java.exe");
        }
        assertTrue(Files.isExecutable(java), "Java executable should exist under java.home");

        Process process = new ProcessBuilder(java.toString(), "-cp", "target/classes",
                "io.github.gulliby.stoptable.Main", "check", "-")
                .directory(Path.of(System.getProperty("user.dir")).toFile())
                .start();
        try {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(INVALID_STDIN);
            }
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "stoptable process should finish within 30 seconds");

            assertEquals(2, process.exitValue());
            assertArrayEquals(new byte[0], process.getInputStream().readAllBytes());
            assertArrayEquals("stoptable: <stdin>: input is not valid UTF-8\n".getBytes(StandardCharsets.UTF_8),
                    process.getErrorStream().readAllBytes());
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(30, TimeUnit.SECONDS);
            }
            process.getInputStream().close();
            process.getErrorStream().close();
        }
    }

    private static String textBlockBetween(String markdown, String startMarker, String endMarker) {
        int start = markdown.indexOf(startMarker);
        assertTrue(start >= 0, "README should contain marker: " + startMarker);
        assertEquals(start, markdown.lastIndexOf(startMarker), "README marker should be unique: " + startMarker);
        int end = markdown.indexOf(endMarker, start + startMarker.length());
        assertTrue(end > start, "README should contain end marker after: " + startMarker);

        Matcher matcher = TEXT_BLOCK.matcher(markdown.substring(start, end));
        assertTrue(matcher.find(), "README section should contain a text code block after: " + startMarker);
        String block = matcher.group(1);
        assertFalse(matcher.find(), "README section should contain only one text code block after: " + startMarker);
        return block;
    }

    private static final class FakeIo implements Io {
        private final byte[] fileBytes;
        private String readPath;
        private int fileReads;
        private int stdinReads;
        private int outCalls;
        private String outText = "";
        private int errCalls;
        private String errText = "";

        private FakeIo(byte[] fileBytes) {
            this.fileBytes = fileBytes;
        }

        @Override
        public byte[] readFile(String path) {
            fileReads++;
            readPath = path;
            return fileBytes;
        }

        @Override
        public byte[] readStdin() {
            stdinReads++;
            return new byte[0];
        }

        @Override
        public void out(String text) {
            outCalls++;
            outText = text;
        }

        @Override
        public void err(String text) {
            errCalls++;
            errText = text;
        }
    }
}
