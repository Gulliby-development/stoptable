package io.github.gulliby.stoptable.format;

import java.io.IOException;

public interface Io {
    byte[] readFile(String path) throws IOException;

    byte[] readStdin() throws IOException;

    void out(String text);

    void err(String text);
}
