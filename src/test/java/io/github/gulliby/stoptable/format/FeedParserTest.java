package io.github.gulliby.stoptable.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.stoptable.core.Failure;
import io.github.gulliby.stoptable.core.Feed;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FeedParserTest {
    private static final String MANY_STOPS = """
            stop S01 Stop 01
            stop S02 Stop 02
            stop S03 Stop 03
            stop S04 Stop 04
            stop S05 Stop 05
            stop S06 Stop 06
            stop S07 Stop 07
            stop S08 Stop 08
            stop S09 Stop 09
            stop S10 Stop 10
            stop S11 Stop 11
            stop S12 Stop 12
            stop S13 Stop 13
            stop S14 Stop 14
            stop S15 Stop 15
            stop S16 Stop 16
            stop S17 Stop 17
            stop S18 Stop 18
            stop S19 Stop 19
            stop S20 Stop 20
            stop S21 Stop 21
            stop S22 Stop 22
            stop S23 Stop 23
            stop S24 Stop 24
            stop S25 Stop 25
            stop S26 Stop 26
            stop S27 Stop 27
            stop S28 Stop 28
            stop S29 Stop 29
            stop S30 Stop 30
            stop S31 Stop 31
            stop S32 Stop 32
            stop S33 Stop 33
            stop S34 Stop 34
            stop S35 Stop 35
            stop S36 Stop 36
            stop S37 Stop 37
            stop S38 Stop 38
            stop S39 Stop 39
            stop S40 Stop 40
            stop S41 Stop 41
            stop S42 Stop 42
            stop S43 Stop 43
            stop S44 Stop 44
            stop S45 Stop 45
            stop S46 Stop 46
            stop S47 Stop 47
            stop S48 Stop 48
            stop S49 Stop 49
            stop S50 Stop 50
            stop S51 Stop 51
            """;

    @Test
    void parsesTheVillageFeedWithDeclarationOrderAndOriginalLines() {
        Feed feed = FeedParser.parse(List.of(
                "# village bus",
                "stop HAR Harbor Pier",
                "stop MKT Market Square",
                "stop CHU Church Lane",
                "stop PRK Park Gate",
                "stop SCH School",
                "service WD mon-fri",
                "service WE sat,sun",
                "",
                "trip 12 WD: HAR 07:00 > MKT 07:10 > CHU 07:18 > PRK 07:30",
                "trip 12 WD: HAR 08:00 > MKT 08:10 > CHU 08:18 > PRK 08:30",
                "trip 14 WD: MKT 07:12 > SCH 07:35 > PRK 07:50",
                "trip 14 WD: MKT 08:15 > SCH 08:35 > PRK 08:50",
                "trip 20 WE: HAR 09:00 > MKT 09:12 > PRK 09:40"));

        assertEquals(List.of(
                new Feed.Stop("HAR", "Harbor Pier", 2),
                new Feed.Stop("MKT", "Market Square", 3),
                new Feed.Stop("CHU", "Church Lane", 4),
                new Feed.Stop("PRK", "Park Gate", 5),
                new Feed.Stop("SCH", "School", 6)), feed.stops());
        assertEquals(List.of(
                new Feed.Service("WD", 31, 7),
                new Feed.Service("WE", 96, 8)), feed.services());
        assertEquals(List.of(
                new Feed.Trip("12", "WD", List.of(
                        new Feed.Call("HAR", 420), new Feed.Call("MKT", 430),
                        new Feed.Call("CHU", 438), new Feed.Call("PRK", 450)), 10),
                new Feed.Trip("12", "WD", List.of(
                        new Feed.Call("HAR", 480), new Feed.Call("MKT", 490),
                        new Feed.Call("CHU", 498), new Feed.Call("PRK", 510)), 11),
                new Feed.Trip("14", "WD", List.of(
                        new Feed.Call("MKT", 432), new Feed.Call("SCH", 455),
                        new Feed.Call("PRK", 470)), 12),
                new Feed.Trip("14", "WD", List.of(
                        new Feed.Call("MKT", 495), new Feed.Call("SCH", 515),
                        new Feed.Call("PRK", 530)), 13),
                new Feed.Trip("20", "WE", List.of(
                        new Feed.Call("HAR", 540), new Feed.Call("MKT", 552),
                        new Feed.Call("PRK", 580)), 14)), feed.trips());
    }

    @Test
    void ignoresBlankAndCommentLinesAndKeepsTabsAsDelimiters() {
        Feed feed = FeedParser.parse(List.of(
                "   ",
                "\t\t",
                "  # comment",
                "\tstop\tA\tAlpha Pier\t",
                "stop B Beta",
                " service WD mon ",
                " trip R WD: A 08:00 > B 08:10 "));

        assertEquals(List.of(new Feed.Stop("A", "Alpha Pier", 4),
                new Feed.Stop("B", "Beta", 5)), feed.stops());
        assertEquals(List.of(new Feed.Trip("R", "WD",
                List.of(new Feed.Call("A", 480), new Feed.Call("B", 490)), 7)), feed.trips());
    }

    @Test
    void acceptsEmptyAndCommentOnlyFeedsWithoutInventingTrips() {
        assertEquals(new Feed(List.of(), List.of(), List.of()), FeedParser.parse(List.of()));
        assertEquals(new Feed(List.of(), List.of(), List.of()),
                FeedParser.parse(List.of("", " ", "\t", "  # comment", "\t# another")));
    }

    @Test
    void retainsNamesAndAllowsSeparateStopAndServiceNamespaces() {
        Feed feed = FeedParser.parse(List.of(
                "stop SHARED A. B'c, D/e- & (F)",
                "service SHARED mon"));

        assertEquals("A. B'c, D/e- & (F)", feed.stops().get(0).name());
        assertEquals(new Feed.Service("SHARED", 1, 2), feed.services().get(0));
    }

    @Test
    void acceptsIdAndNameLengthBoundariesAndAllAllowedNamePunctuation() {
        Feed feed = FeedParser.parse(List.of(
                "stop A X",
                "stop A_123456 123456789012345678901234567890"));

        assertEquals("X", feed.stops().get(0).name());
        assertEquals("A_123456", feed.stops().get(1).id());
        assertEquals(30, feed.stops().get(1).name().length());
    }

    @Test
    void inlineHashIsPartOfTheStopNameAndIsRejectedThere() {
        assertFormat(1, "invalid stop name \"Alpha?#?note\"",
                "stop A Alpha # note");
    }

    @Test
    void aNonAsciiWhitespaceCharacterIsNotAFeedDelimiter() {
        assertFormat(1, "unknown keyword \"?stop\"",
                "\u00a0stop A Alpha");
    }

    @ParameterizedTest
    @MethodSource("daySets")
    void parsesDayNamesRangesAndUnions(String text, int expected) {
        assertEquals(expected, FeedParser.parseDays(text));
        assertEquals(new Feed.Service("WD", expected, 1),
                FeedParser.parse(List.of("service WD " + text)).services().get(0));
    }

    private static Stream<Arguments> daySets() {
        return Stream.of(
                Arguments.of("mon", 1),
                Arguments.of("tue", 2),
                Arguments.of("wed", 4),
                Arguments.of("thu", 8),
                Arguments.of("fri", 16),
                Arguments.of("sat", 32),
                Arguments.of("sun", 64),
                Arguments.of("mon-fri", 31),
                Arguments.of("sat,sun", 96),
                Arguments.of("mon-wed,fri,sun", 87),
                Arguments.of("mon,mon", 1),
                Arguments.of("mon-wed,tue-thu", 15),
                Arguments.of("sun-sun", 64),
                Arguments.of("mon-sun", 127),
                Arguments.of("fri-sat", 48),
                Arguments.of("sun,sat", 96),
                Arguments.of("tue-thu", 14));
    }

    @ParameterizedTest
    @MethodSource("invalidDaySets")
    void rejectsInvalidDayLists(String text) {
        assertEquals(-1, FeedParser.parseDays(text));

    }

    private static Stream<Arguments> invalidDaySets() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("Mon"),
                Arguments.of("xxx"),
                Arguments.of("fri-mon"),
                Arguments.of("mon-"),
                Arguments.of("-mon"),
                Arguments.of("mon--tue"),
                Arguments.of("mon,,tue"),
                Arguments.of(",mon"),
                Arguments.of("mon,"),
                Arguments.of("mon ,tue"),
                Arguments.of("mon, tue"),
                Arguments.of(" mon"),
                Arguments.of("sun-"),
                Arguments.of("mon-tue-wed"),
                Arguments.of("mon--"),
                Arguments.of("mo"),
                Arguments.of("mon\n"),
                Arguments.of("mon\t"),
                Arguments.of("😀"));
    }

    @Test
    void whitespaceInsideDaysChangesServiceArityBeforeDayValidation() {
        assertFormat(1, "expected \"service ID DAYS\"", "service WD mon ,tue");
    }

    @ParameterizedTest
    @MethodSource("stopErrors")
    void reportsStopDeclarationErrorsInSpecifiedOrder(List<String> lines, int line, String message) {
        assertFormat(line, message, lines);
    }

    private static Stream<Arguments> stopErrors() {
        return Stream.of(
                Arguments.of(List.of("stop"), 1, "expected \"stop ID NAME\""),
                Arguments.of(List.of("stop A"), 1, "expected \"stop ID NAME\""),
                Arguments.of(List.of("stop  Alpha"), 1, "expected \"stop ID NAME\""),
                Arguments.of(List.of("stop bad-id Alpha"), 1, "invalid stop id \"bad-id\""),
                Arguments.of(List.of("stop A-1234567 Alpha"), 1, "invalid stop id \"A-1234567\""),
                Arguments.of(List.of("stop A_1234567 Alpha"), 1, "invalid stop id \"A_1234567\""),
                Arguments.of(List.of("stop ? Alpha"), 1, "invalid stop id \"?\""),
                Arguments.of(List.of("stop bad-id é"), 1, "invalid stop id \"bad-id\""),
                Arguments.of(List.of("stop A"), 1, "expected \"stop ID NAME\""),
                Arguments.of(List.of("stop A 1234567890123456789012345678901"), 1,
                        "invalid stop name \"1234567890123456789012345678901\""),
                Arguments.of(List.of("stop A Alpha\tPier"), 1, "invalid stop name \"Alpha?Pier\""),
                Arguments.of(List.of("stop A Café"), 1, "invalid stop name \"Caf?\""),
                Arguments.of(List.of("stop A Alpha!", "stop A"), 1, "invalid stop name \"Alpha!\""),
                Arguments.of(List.of("stop A Alpha", "stop A Beta!"), 2, "invalid stop name \"Beta!\""),
                Arguments.of(List.of("stop A Alpha", "stop A Beta"), 2, "duplicate stop \"A\""));
    }

    @Test
    void acceptsAnEightCharacterIdAndAThirtyCharacterName() {
        Feed feed = FeedParser.parse(List.of("stop A_123456 123456789012345678901234567890"));

        assertEquals(new Feed.Stop("A_123456", "123456789012345678901234567890", 1),
                feed.stops().get(0));
    }

    @ParameterizedTest
    @MethodSource("serviceErrors")
    void reportsServiceDeclarationErrorsInSpecifiedOrder(List<String> lines, int line, String message) {
        assertFormat(line, message, lines);
    }

    private static Stream<Arguments> serviceErrors() {
        return Stream.of(
                Arguments.of(List.of("service"), 1, "expected \"service ID DAYS\""),
                Arguments.of(List.of("service WD"), 1, "expected \"service ID DAYS\""),
                Arguments.of(List.of("service WD mon extra"), 1, "expected \"service ID DAYS\""),
                Arguments.of(List.of("service bad-id invalid"), 1, "invalid service id \"bad-id\""),
                Arguments.of(List.of("service A_1234567 mon"), 1, "invalid service id \"A_1234567\""),
                Arguments.of(List.of("service S1 invalid"), 1, "invalid days \"invalid\""),
                Arguments.of(List.of("service WD mon", "service WD invalid"), 2,
                        "invalid days \"invalid\""),
                Arguments.of(List.of("service WD mon", "service WD tue"), 2,
                        "duplicate service \"WD\""),
                Arguments.of(List.of("service A_1234567 mon"), 1, "invalid service id \"A_1234567\""),
                Arguments.of(List.of("service WD MON"), 1, "invalid days \"MON\""),
                Arguments.of(List.of("service WD mon-fri", "service WD sat,sun"), 2,
                        "duplicate service \"WD\""));
    }

    @Test
    void firstInvalidLineWinsAndItsOriginalLineNumberIsReported() {
        assertFormat(5, "invalid stop id \"bad-id\"",
                "# comment", "", "stop A Alpha", "\t", "stop bad-id Later");
    }

    @ParameterizedTest
    @MethodSource("tripErrors")
    void reportsTripErrorsAndCompetingFaultsInSpecifiedOrder(
            List<String> lines, int line, String message) {
        assertFormat(line, message, lines);
    }

    private static Stream<Arguments> tripErrors() {
        return Stream.of(
                Arguments.of(List.of("trip R WD A 08:00 > B 08:10"), 1,
                        "expected \"trip ROUTE SERVICE: STOP TIME > STOP TIME\""),
                Arguments.of(List.of("trip R WD EXTRA: A 08:00 > B 08:10"), 1,
                        "expected \"trip ROUTE SERVICE: STOP TIME > STOP TIME\""),
                Arguments.of(List.of("trip bad-id MISSING: NO bad"), 1, "invalid route \"bad-id\""),
                Arguments.of(List.of("trip R MISSING: NO bad"), 1, "unknown service \"MISSING\""),
                Arguments.of(List.of("stop A Alpha", "trip R WD: NO bad"), 2, "unknown service \"WD\""),
                Arguments.of(List.of("service WD mon", "trip R WD:"), 2, "expected \"STOP TIME\""),
                Arguments.of(List.of("stop A Alpha", "service WD mon", "trip R WD: A 08:00 >"),
                        3, "expected \"STOP TIME\""),
                Arguments.of(List.of("stop A Alpha", "service WD mon", "trip R WD: A 08:00 > > A 08:10"),
                        3, "expected \"STOP TIME\""),
                Arguments.of(List.of("service WD mon", "trip R WD: A 08:00 > B 08:10"),
                        2, "unknown stop \"A\""),
                Arguments.of(List.of("stop A Alpha", "service WD mon", "trip R WD: NO bad"),
                        3, "unknown stop \"NO\""),
                Arguments.of(List.of("stop A Alpha", "stop B Beta", "service WD mon",
                                "trip R WD: A nope > B 08:10"),
                        4, "invalid time \"nope\""),
                Arguments.of(List.of("stop A Alpha", "stop B Beta", "service WD mon",
                                "trip R WD: A 08:00 > A nope"),
                        4, "invalid time \"nope\""),
                Arguments.of(List.of("stop A Alpha", "stop B Beta", "service WD mon",
                                "trip R WD: A 08:00 > A 08:00"),
                        4, "stop \"A\" is used twice in one trip"),
                Arguments.of(List.of("stop A Alpha", "stop B Beta", "service WD mon",
                                "trip R WD: A 08:00 > B 08:00"),
                        4, "time must be later than the previous stop"),
                Arguments.of(List.of("stop A Alpha", "stop B Beta", "service WD mon",
                                "trip R WD: A 08:00 > B 07:59"),
                        4, "time must be later than the previous stop"),
                Arguments.of(List.of("stop A Alpha", "stop B Beta", "service WD mon",
                                "trip R WD: A 7:05 > B 08:10"),
                        4, "invalid time \"7:05\""),
                Arguments.of(List.of("stop A Alpha", "stop B Beta", "service WD mon",
                                "trip R WD: A 24:00 > B 08:10"),
                        4, "invalid time \"24:00\""),
                Arguments.of(List.of("stop A Alpha", "stop B Beta", "service WD mon",
                                "trip R WD: A 12:60 > B 13:00"),
                        4, "invalid time \"12:60\""),
                Arguments.of(List.of("stop A Alpha", "stop B Beta", "service WD mon",
                                "trip R WD: A ٠٧:٠٥ > B 08:10"),
                        4, "invalid time \"??:??\""),
                Arguments.of(List.of("stop A Alpha", "stop B Beta", "service WD mon",
                                "trip R WD: A 08:00:00 > B 08:10"),
                        4, "invalid time \"08:00:00\""),
                Arguments.of(List.of("stop A Alpha", "service WD mon", "trip R WD: A 00:00"),
                        3, "a trip needs at least 2 stops"),
                Arguments.of(List.of("stop A Alpha", "stop B Beta", "service WD mon",
                                "trip R WD: A 08:00 > B 08:00"),
                        4, "time must be later than the previous stop"),
                Arguments.of(List.of("service WD mon", "trip R WD: A 08:00 > B 08:10"),
                        2, "unknown stop \"A\""));
    }

    @Test
    void acceptsTheMinimumTwoCallsAndTheMaximumFiftyCalls() {
        Feed twoCalls = FeedParser.parse(List.of(
                "stop A Alpha",
                "stop B Beta",
                "service WD mon",
                "trip R WD: A 00:00 > B 23:59"));
        assertEquals(2, twoCalls.trips().get(0).calls().size());

        Feed fiftyCalls = FeedParser.parse(lines(MANY_STOPS
                + "service WD mon\n"
                + "trip R WD: S01 00:00 > S02 00:01 > S03 00:02 > S04 00:03 > S05 00:04"
                + " > S06 00:05 > S07 00:06 > S08 00:07 > S09 00:08 > S10 00:09"
                + " > S11 00:10 > S12 00:11 > S13 00:12 > S14 00:13 > S15 00:14"
                + " > S16 00:15 > S17 00:16 > S18 00:17 > S19 00:18 > S20 00:19"
                + " > S21 00:20 > S22 00:21 > S23 00:22 > S24 00:23 > S25 00:24"
                + " > S26 00:25 > S27 00:26 > S28 00:27 > S29 00:28 > S30 00:29"
                + " > S31 00:30 > S32 00:31 > S33 00:32 > S34 00:33 > S35 00:34"
                + " > S36 00:35 > S37 00:36 > S38 00:37 > S39 00:38 > S40 00:39"
                + " > S41 00:40 > S42 00:41 > S43 00:42 > S44 00:43 > S45 00:44"
                + " > S46 00:45 > S47 00:46 > S48 00:47 > S49 00:48 > S50 00:49"));
        assertEquals(50, fiftyCalls.trips().get(0).calls().size());
    }

    @Test
    void rejectsFiftyOneValidCallsOnlyAfterAllCallValidation() {
        assertFormat(53, "a trip has at most 50 stops",
                lines(MANY_STOPS
                        + "service WD mon\n"
                        + "trip R WD: S01 00:00 > S02 00:01 > S03 00:02 > S04 00:03 > S05 00:04"
                        + " > S06 00:05 > S07 00:06 > S08 00:07 > S09 00:08 > S10 00:09"
                        + " > S11 00:10 > S12 00:11 > S13 00:12 > S14 00:13 > S15 00:14"
                        + " > S16 00:15 > S17 00:16 > S18 00:17 > S19 00:18 > S20 00:19"
                        + " > S21 00:20 > S22 00:21 > S23 00:22 > S24 00:23 > S25 00:24"
                        + " > S26 00:25 > S27 00:26 > S28 00:27 > S29 00:28 > S30 00:29"
                        + " > S31 00:30 > S32 00:31 > S33 00:32 > S34 00:33 > S35 00:34"
                        + " > S36 00:35 > S37 00:36 > S38 00:37 > S39 00:38 > S40 00:39"
                        + " > S41 00:40 > S42 00:41 > S43 00:42 > S44 00:43 > S45 00:44"
                        + " > S46 00:45 > S47 00:46 > S48 00:47 > S49 00:48 > S50 00:49"
                        + " > S51 00:50"));
    }

    @Test
    void rejectsMalformedFiftyFirstCallBeforeApplyingTripCountLimit() {
        assertFormat(53, "invalid time \"nope\"",
                lines(MANY_STOPS
                        + "service WD mon\n"
                        + "trip R WD: S01 00:00 > S02 00:01 > S03 00:02 > S04 00:03 > S05 00:04"
                        + " > S06 00:05 > S07 00:06 > S08 00:07 > S09 00:08 > S10 00:09"
                        + " > S11 00:10 > S12 00:11 > S13 00:12 > S14 00:13 > S15 00:14"
                        + " > S16 00:15 > S17 00:16 > S18 00:17 > S19 00:18 > S20 00:19"
                        + " > S21 00:20 > S22 00:21 > S23 00:22 > S24 00:23 > S25 00:24"
                        + " > S26 00:25 > S27 00:26 > S28 00:27 > S29 00:28 > S30 00:29"
                        + " > S31 00:30 > S32 00:31 > S33 00:32 > S34 00:33 > S35 00:34"
                        + " > S36 00:35 > S37 00:36 > S38 00:37 > S39 00:38 > S40 00:39"
                        + " > S41 00:40 > S42 00:41 > S43 00:42 > S44 00:43 > S45 00:44"
                        + " > S46 00:45 > S47 00:46 > S48 00:47 > S49 00:48 > S50 00:49"
                        + " > S51 nope"));
    }

    @Test
    void validatesMalformedCallsBeforeApplyingTripCountLimits() {
        assertFormat(3, "unknown stop \"MISSING\"",
                "stop A Alpha", "service WD mon", "trip R WD: A 08:00 > MISSING 08:10");
        assertFormat(3, "a trip needs at least 2 stops",
                "stop A Alpha", "service WD mon", "trip R WD: A 08:00");
    }

    @Test
    void allowsRepeatedRoutesAndKeepsTripLineNumbers() {
        Feed feed = FeedParser.parse(List.of(
                "stop A Alpha",
                "stop B Beta",
                "service WD mon",
                "trip R WD: A 08:00 > B 08:10",
                "trip R WD: A 09:00 > B 09:10"));

        assertEquals(4, feed.trips().get(0).line());
        assertEquals(5, feed.trips().get(1).line());
    }

    @Test
    void reportsTheFirstMalformedLineEvenWhenALaterLineIsAlsoInvalid() {
        assertFormat(2, "unknown keyword \"Stop\"",
                "stop A Alpha", "Stop A Beta", "service WD not-a-day");
    }

    private static List<String> lines(String text) {
        return text.lines().toList();
    }

    private static void assertFormat(int expectedLine, String expectedMessage, String... lines) {
        assertFormat(expectedLine, expectedMessage, List.of(lines));
    }

    private static void assertFormat(int expectedLine, String expectedMessage, List<String> lines) {
        Failure.Format failure = assertThrows(Failure.Format.class, () -> FeedParser.parse(lines));
        assertEquals(expectedLine, failure.line());
        assertEquals(expectedMessage, failure.getMessage());
        assertTrue(failure.line() > 0);
    }

}
