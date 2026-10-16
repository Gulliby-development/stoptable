package io.github.gulliby.stoptable;

import io.github.gulliby.stoptable.format.Io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Io io = new Io() {
            @Override
            public byte[] readFile(String path) throws IOException {
                return Files.readAllBytes(Path.of(path));
            }

            @Override
            public byte[] readStdin() throws IOException {
                return System.in.readAllBytes();
            }

            @Override
            public void out(String text) {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                System.out.write(bytes, 0, bytes.length);
                System.out.flush();
            }

            @Override
            public void err(String text) {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                System.err.write(bytes, 0, bytes.length);
                System.err.flush();
            }
        };
        System.exit(Cli.run(List.of(args), io));
    }
}
