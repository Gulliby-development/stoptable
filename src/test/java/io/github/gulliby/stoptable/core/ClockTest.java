package io.github.gulliby.stoptable.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class ClockTest {
    @Test
    void usageFailurePreservesItsMessage() {
        Failure.Usage failure = assertThrows(Failure.Usage.class, () -> {
            throw new Failure.Usage("bad");
        });

        assertEquals("bad", failure.getMessage());
    }

    @Test
    void formatFailurePreservesItsMessageAndLine() {
        Failure.Format failure = assertThrows(Failure.Format.class, () -> {
            throw new Failure.Format(12, "bad");
        });

        assertEquals("bad", failure.getMessage());
        assertEquals(12, failure.line());
    }

    @ParameterizedTest
    @CsvSource({
            "00:00, 0",
            "00:01, 1",
            "07:05, 425",
            "12:34, 754",
            "23:59, 1439"
    })
    void parsesValidAsciiTimes(String text, int expectedMinutes) {
        assertEquals(expectedMinutes, Clock.parse(text));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "7:05",
            "07:5",
            "007:05",
            "+7:05",
            "07-05",
            "07/05",
            "24:00",
            "99:00",
            "23:60",
            "07:99",
            "a7:05",
            "07:a5",
            " 07:05",
            "07:05 ",
            "\t07:05",
            "٠7:05",
            "07:５９"
    })
    void rejectsMalformedOrOutOfRangeTimes(String text) {
        assertEquals(-1, Clock.parse(text));
    }

    @ParameterizedTest
    @CsvSource({
            "0, 00:00",
            "1, 00:01",
            "59, 00:59",
            "60, 01:00",
            "425, 07:05",
            "1439, 23:59"
    })
    void formatsValidMinuteValues(int minutes, String expectedText) {
        assertEquals(expectedText, Clock.format(minutes));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 59, 60, 425, 1439})
    void representativeMinuteValuesRoundTrip(int minutes) {
        assertEquals(minutes, Clock.parse(Clock.format(minutes)));
    }
}
