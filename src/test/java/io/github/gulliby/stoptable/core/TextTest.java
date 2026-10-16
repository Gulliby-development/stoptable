package io.github.gulliby.stoptable.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TextTest {
    @ParameterizedTest
    @MethodSource("showCases")
    void showQuotesTextAndReplacesUnsupportedCodePoints(String input, String expected) {
        assertEquals(expected, Text.show(input));
    }

    private static Stream<Arguments> showCases() {
        return Stream.of(
                Arguments.of("A!~\"\\Z", "\"A!~\"\\Z\""),
                Arguments.of("", "\"\""),
                Arguments.of(" ", "\"?\""),
                Arguments.of("\t", "\"?\""),
                Arguments.of("\r", "\"?\""),
                Arguments.of("\n", "\"?\""),
                Arguments.of("\u007f", "\"?\""),
                Arguments.of("é", "\"?\""),
                Arguments.of("😀", "\"?\""),
                Arguments.of("A é 😀!", "\"A????!\"")
        );
    }

    @ParameterizedTest
    @MethodSource("listOrCases")
    void listOrFormatsOneThroughFourItems(List<String> items, String expected) {
        assertEquals(expected, Text.listOr(items));
    }

    private static Stream<Arguments> listOrCases() {
        return Stream.of(
                Arguments.of(List.of("A"), "A"),
                Arguments.of(List.of("A", "B"), "A or B"),
                Arguments.of(List.of("A", "B", "C"), "A, B or C"),
                Arguments.of(List.of("A", "B", "C", "D"), "A, B, C or D")
        );
    }

    @ParameterizedTest
    @MethodSource("pluralCases")
    void pluralUsesSingularOnlyForOne(int count, String expected) {
        assertEquals(expected, Text.plural(count, "stop", "stops"));
    }

    private static Stream<Arguments> pluralCases() {
        return Stream.of(
                Arguments.of(-1, "stops"),
                Arguments.of(0, "stops"),
                Arguments.of(1, "stop"),
                Arguments.of(2, "stops")
        );
    }

    @Test
    void renderTableKeepsHeaderWhenThereAreNoRows() {
        List<String> rendered = Text.renderTable(List.of("time", "route", "to"), List.of());

        assertEquals(List.of("time  route  to"), rendered);
        assertCleanTableLines(rendered);
    }

    @Test
    void renderTableMatchesThePrescribedDepartureBoard() {
        List<String> rendered = Text.renderTable(
                List.of("time", "route", "to"),
                List.of(
                        List.of("07:12", "14", "Park Gate"),
                        List.of("08:10", "12", "Park Gate")));

        assertEquals(List.of(
                "time   route  to",
                "07:12  14     Park Gate",
                "08:10  12     Park Gate"), rendered);
        assertCleanTableLines(rendered);
    }

    @Test
    void renderTableIncludesHeaderAndRowWidthsAndPreservesEmptyCellsAndOrder() {
        List<String> rendered = Text.renderTable(
                List.of("x", "y", "z"),
                List.of(
                        List.of("", "longer", ""),
                        List.of("last", "", "end")));

        assertEquals(List.of(
                "x     y       z",
                "      longer",
                "last          end"), rendered);
        assertCleanTableLines(rendered);
    }

    @Test
    void renderTableUsesHeaderWidthsWhenTheyExceedEveryRowCell() {
        List<String> rendered = Text.renderTable(
                List.of("description", "x"),
                List.of(List.of("d", "y")));

        assertEquals(List.of("description  x", "d            y"), rendered);
        assertCleanTableLines(rendered);
    }

    private static void assertCleanTableLines(List<String> lines) {
        for (String line : lines) {
            assertFalse(line.endsWith(" "));
            assertFalse(line.contains("\n"));
            assertFalse(line.contains("\r"));
        }
    }
}
