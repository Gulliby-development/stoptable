package io.github.gulliby.stoptable.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OptionsTest {
    private static final Options.Spec DAY =
            Options.Spec.choice("--day", List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun"), 0);
    private static final Options.Spec AFTER = Options.Spec.time("--after", 0);
    private static final Options.Spec LIMIT = Options.Spec.integer("--limit", 1, 99, 10);
    private static final Options.Spec TRANSFER = Options.Spec.integer("--transfer", 0, 120, 0);
    private static final Options.Spec FORMAT =
            Options.Spec.choice("--format", List.of("dot", "mermaid"), 0);
    private static final List<Options.Spec> SPECS = List.of(DAY, AFTER, LIMIT, TRANSFER, FORMAT);

    @Test
    void factoriesAndRecordsExposeTheirDeclaredValues() {
        Options.Spec integer = Options.Spec.integer("--number", 0, 120, 7);
        Options.Spec time = Options.Spec.time("--when", 425);
        Options.Spec choice = Options.Spec.choice("--format", List.of("dot", "mermaid"), 1);

        assertEquals(new Options.Spec("--number", Options.Kind.INT, 0, 120, List.of(), 7), integer);
        assertEquals(new Options.Spec("--when", Options.Kind.TIME, 0, 0, List.of(), 425), time);
        assertEquals(new Options.Spec("--format", Options.Kind.CHOICE, 0, 0, List.of("dot", "mermaid"), 1), choice);
        assertEquals(Options.Kind.INT, integer.kind());
        assertEquals(Options.Kind.TIME, time.kind());
        assertEquals(Options.Kind.CHOICE, choice.kind());
        assertEquals(Options.Kind.values().length, 3);

        Options.Parsed parsed = new Options.Parsed(Map.of("--number", 7), List.of("item"));
        assertEquals(Map.of("--number", 7), parsed.values());
        assertEquals(List.of("item"), parsed.args());
    }

    @Test
    void ordinaryArgumentsAndSingleDashStayPositional() {
        Options.Parsed parsed = Options.parse(
                List.of("first", "-", "--limit", "2", "last"), SPECS);

        assertEquals(List.of("first", "-", "last"), parsed.args());
        assertEquals(2, parsed.values().get("--limit"));
    }

    @ParameterizedTest
    @MethodSource("acceptedForms")
    void parsesFollowingAndAssignedValues(String[] argv, String name, int expected) {
        Options.Parsed parsed = Options.parse(List.of(argv), SPECS);

        assertEquals(expected, parsed.values().get(name));
    }

    private static Stream<Arguments> acceptedForms() {
        return Stream.of(
                Arguments.of(new String[] {"--limit", "2"}, "--limit", 2),
                Arguments.of(new String[] {"--limit=2"}, "--limit", 2),
                Arguments.of(new String[] {"--format=dot"}, "--format", 0),
                Arguments.of(new String[] {"--format=mermaid"}, "--format", 1));
    }

    @Test
    void assignmentSplitsOnlyAtTheFirstEqualsSign() {
        assertUsage(
                "invalid --format \"dot=extra\" (expected dot or mermaid)",
                List.of("--format=dot=extra"),
                SPECS);
    }

    @ParameterizedTest
    @MethodSource("unknownOptions")
    void reportsTheEntireOriginalUnknownOption(String token, String expected) {
        assertUsage(expected, List.of(token), SPECS);
    }

    private static Stream<Arguments> unknownOptions() {
        return Stream.of(
                Arguments.of("-x", "unknown option \"-x\""),
                Arguments.of("--", "unknown option \"--\""),
                Arguments.of("--help", "unknown option \"--help\""),
                Arguments.of("--unknown=x", "unknown option \"--unknown=x\""),
                Arguments.of("--mystery=bad \té😀", "unknown option \"--mystery=bad????\""));
    }

    @Test
    void reportsMissingValueOnlyWhenNoNextTokenExists() {
        assertUsage("--limit needs a value", List.of("--limit"), SPECS);
    }

    @ParameterizedTest
    @MethodSource("dashPrefixedAndInvalidConsumedValues")
    void consumesNextTokenAsAValueEvenWhenItStartsWithDash(String value, String expected) {
        assertUsage(expected, List.of("--limit", value), SPECS);
    }

    private static Stream<Arguments> dashPrefixedAndInvalidConsumedValues() {
        return Stream.of(
                Arguments.of("last", "invalid --limit \"last\" (expected an integer from 1 to 99)"),
                Arguments.of("-1", "invalid --limit \"-1\" (expected an integer from 1 to 99)"),
                Arguments.of("--help", "invalid --limit \"--help\" (expected an integer from 1 to 99)"));
    }

    @ParameterizedTest
    @MethodSource("repeatedValues")
    void lastRepeatedValueWins(String[] argv, int expected) {
        assertEquals(expected, Options.parse(List.of(argv), SPECS).values().get("--limit"));
    }

    private static Stream<Arguments> repeatedValues() {
        return Stream.of(
                Arguments.of(new String[] {"--limit", "bad", "--limit", "2"}, 2),
                Arguments.of(new String[] {"--limit", "2", "--limit=3"}, 3));
    }

    @Test
    void invalidEarlierValueIsIgnoredWhenItWasOverwritten() {
        assertUsage(
                "invalid --limit \"bad\" (expected an integer from 1 to 99)",
                List.of("--limit", "2", "--limit", "bad"),
                SPECS);
    }

    @Test
    void absentOptionsKeepFallbacksAndCreateOneValuePerSpec() {
        Options.Parsed parsed = Options.parse(List.of(), SPECS);

        assertEquals(Map.of("--day", 0, "--after", 0, "--limit", 10, "--transfer", 0, "--format", 0),
                parsed.values());
        assertEquals(SPECS.size(), parsed.values().size());
        assertEquals(List.of(), parsed.args());
    }

    @ParameterizedTest
    @MethodSource("validIntegerValues")
    void acceptsIntegerSyntaxAndInclusiveBounds(String value, int lo, int hi, int expected) {
        Options.Spec spec = Options.Spec.integer("--number", lo, hi, 42);

        assertEquals(expected,
                Options.parse(List.of("--number", value), List.of(spec)).values().get("--number"));
    }

    private static Stream<Arguments> validIntegerValues() {
        return Stream.of(
                Arguments.of("0", 0, 120, 0),
                Arguments.of("120", 0, 120, 120),
                Arguments.of("1", 1, 99, 1),
                Arguments.of("99", 1, 99, 99),
                Arguments.of("999999999", 0, 999999999, 999999999));
    }

    @ParameterizedTest
    @MethodSource("invalidIntegerSyntax")
    void rejectsInvalidIntegerSpellings(List<String> argv, String expected) {
        assertUsage(expected, argv, SPECS);
    }

    private static Stream<Arguments> invalidIntegerSyntax() {
        return Stream.of(
                Arguments.of(List.of("--limit", "-1"),
                        "invalid --limit \"-1\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("--limit", "+3"),
                        "invalid --limit \"+3\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("--limit", "007"),
                        "invalid --limit \"007\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("--limit", "00"),
                        "invalid --limit \"00\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("--limit", "١"),
                        "invalid --limit \"?\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("--limit", " 2"),
                        "invalid --limit \"?2\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("--limit", ""),
                        "invalid --limit \"\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("--limit=1000000000"),
                        "invalid --limit \"1000000000\" (expected an integer from 1 to 99)"),
                Arguments.of(List.of("--limit="),
                        "invalid --limit \"\" (expected an integer from 1 to 99)"));
    }

    @ParameterizedTest
    @MethodSource("outOfRangeIntegers")
    void rejectsValuesOutsideInclusiveBounds(String value, int lo, int hi) {
        Options.Spec spec = Options.Spec.integer("--number", lo, hi, 5);

        assertUsage(
                "invalid --number \"" + value + "\" (expected an integer from " + lo + " to " + hi + ")",
                List.of("--number", value),
                List.of(spec));
    }

    private static Stream<Arguments> outOfRangeIntegers() {
        return Stream.of(
                Arguments.of("0", 1, 99),
                Arguments.of("100", 1, 99),
                Arguments.of("121", 0, 120));
    }

    @ParameterizedTest
    @MethodSource("validTimes")
    void parsesTimeEndpoints(String value, int expectedMinutes) {
        Options.Spec spec = Options.Spec.time("--when", 0);

        assertEquals(expectedMinutes,
                Options.parse(List.of("--when", value), List.of(spec)).values().get("--when"));
    }

    private static Stream<Arguments> validTimes() {
        return Stream.of(
                Arguments.of("00:00", 0),
                Arguments.of("23:59", 1439));
    }

    @ParameterizedTest
    @MethodSource("invalidTimes")
    void reportsInvalidTimes(String value) {
        assertUsage(
                "invalid --after \"" + value + "\" (expected a time from 00:00 to 23:59)",
                List.of("--after", value),
                SPECS);
    }

    private static Stream<Arguments> invalidTimes() {
        return Stream.of(
                Arguments.of("24:00"),
                Arguments.of("7:05"));
    }

    @ParameterizedTest
    @MethodSource("validChoices")
    void returnsTheExactChoiceIndex(String value, int expectedIndex) {
        assertEquals(expectedIndex,
                Options.parse(List.of("--format=" + value), SPECS).values().get("--format"));
    }

    private static Stream<Arguments> validChoices() {
        return Stream.of(
                Arguments.of("dot", 0),
                Arguments.of("mermaid", 1));
    }

    @Test
    void rejectsChoiceCaseVariantsWithTheFullDiagnostic() {
        assertUsage(
                "invalid --format \"DOT\" (expected dot or mermaid)",
                List.of("--format=DOT"),
                SPECS);
    }

    @Test
    void scansAllOptionsBeforeValidatingRetainedValues() {
        assertUsage(
                "unknown option \"--unknown\"",
                List.of("--limit", "bad", "--unknown"),
                SPECS);
    }

    @ParameterizedTest
    @MethodSource("scanErrorsInArgumentOrder")
    void reportsTheFirstScanErrorFromLeftToRight(List<String> argv, String expected) {
        assertUsage(expected, argv, SPECS);
    }

    private static Stream<Arguments> scanErrorsInArgumentOrder() {
        return Stream.of(
                Arguments.of(List.of("--unknown", "--limit"), "unknown option \"--unknown\""),
                Arguments.of(List.of("--limit", "x", "--unknown"), "unknown option \"--unknown\""),
                Arguments.of(List.of("--limit"), "--limit needs a value"));
    }

    @ParameterizedTest
    @MethodSource("validationErrorsInSpecOrder")
    void validatesInSpecOrderRatherThanArgumentOrder(List<String> argv, String expected) {
        assertUsage(expected, argv, SPECS);
    }

    private static Stream<Arguments> validationErrorsInSpecOrder() {
        return Stream.of(
                Arguments.of(
                        List.of("--limit", "bad", "--after", "bad", "--day", "soon"),
                        "invalid --day \"soon\" (expected mon, tue, wed, thu, fri, sat or sun)"),
                Arguments.of(
                        List.of("--limit", "bad", "--after", "bad", "--day", "mon"),
                        "invalid --after \"bad\" (expected a time from 00:00 to 23:59)"));
    }

    @Test
    void sanitizesSpacesControlsAndSupplementaryCodePointsInValues() {
        assertUsage(
                "invalid --limit \"a????\" (expected an integer from 1 to 99)",
                List.of("--limit=a \té😀"),
                SPECS);
    }

    private static void assertUsage(String expected, List<String> argv, List<Options.Spec> specs) {
        Failure.Usage failure = assertThrows(
                Failure.Usage.class,
                () -> Options.parse(argv, specs));
        assertEquals(expected, failure.getMessage());
    }
}
