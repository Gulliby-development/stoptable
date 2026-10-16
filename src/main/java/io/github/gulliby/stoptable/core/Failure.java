package io.github.gulliby.stoptable.core;

public final class Failure {
    private Failure() {
    }

    public static final class Usage extends RuntimeException {
        public Usage(String message) {
            super(message);
        }
    }

    public static final class Format extends RuntimeException {
        private final int line;

        public Format(int line, String message) {
            super(message);
            this.line = line;
        }

        public int line() {
            return line;
        }
    }
}
