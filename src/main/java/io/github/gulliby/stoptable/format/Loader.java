package io.github.gulliby.stoptable.format;

import io.github.gulliby.stoptable.core.Failure;
import io.github.gulliby.stoptable.core.Feed;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.InvalidPathException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Loader {
    private Loader() {
    }

    public static String displayPath(String path) {
        if (path.equals("-")) {
            return "<stdin>";
        }
        return path.replace('\\', '/');
    }

    public static List<String> splitLines(String text) {
        String input = text.startsWith("\uFEFF") ? text.substring(1) : text;
        if (input.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < input.length(); index++) {
            if (input.charAt(index) == '\n') {
                int end = index;
                if (end > start && input.charAt(end - 1) == '\r') {
                    end--;
                }
                lines.add(input.substring(start, end));
                start = index + 1;
            }
        }
        if (start < input.length()) {
            lines.add(input.substring(start));
        }
        return List.copyOf(lines);
    }

    public record Source(String display, String text) {
    }

    public static Source readText(Io io, String path) {
        String display = displayPath(path);
        byte[] bytes;
        try {
            bytes = path.equals("-") ? io.readStdin() : io.readFile(path);
        } catch (IOException | InvalidPathException exception) {
            throw new Failure.Usage(display + ": cannot read the file");
        }

        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new Failure.Usage(display + ": input is not valid UTF-8");
        }
        return new Source(display, text);
    }

    public record Loaded(String display, Feed feed) {
    }

    public static Loaded loadFeed(Io io, String path) {
        Source source = readText(io, path);
        Feed feed;
        try {
            feed = FeedParser.parse(splitLines(source.text()));
        } catch (Failure.Format failure) {
            throw new Failure.Usage(source.display() + ":" + failure.line() + ": " + failure.getMessage());
        }
        if (feed.trips().isEmpty()) {
            throw new Failure.Usage(source.display() + ": no trips");
        }
        return new Loaded(source.display(), feed);
    }
}
