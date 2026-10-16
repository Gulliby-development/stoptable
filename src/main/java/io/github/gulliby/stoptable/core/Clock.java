package io.github.gulliby.stoptable.core;

public final class Clock {
    private Clock() {
    }

    public static int parse(String text) {
        if (text == null || text.length() != 5
                || text.charAt(2) != ':'
                || !isAsciiDigit(text.charAt(0))
                || !isAsciiDigit(text.charAt(1))
                || !isAsciiDigit(text.charAt(3))
                || !isAsciiDigit(text.charAt(4))) {
            return -1;
        }

        int hours = (text.charAt(0) - '0') * 10 + text.charAt(1) - '0';
        int minutes = (text.charAt(3) - '0') * 10 + text.charAt(4) - '0';
        if (hours > 23 || minutes > 59) {
            return -1;
        }
        return hours * 60 + minutes;
    }

    public static String format(int minutes) {
        int hours = minutes / 60;
        int minutePart = minutes % 60;
        return new String(new char[] {
                (char) ('0' + hours / 10),
                (char) ('0' + hours % 10),
                ':',
                (char) ('0' + minutePart / 10),
                (char) ('0' + minutePart % 10)
        });
    }

    private static boolean isAsciiDigit(char character) {
        return character >= '0' && character <= '9';
    }
}
