package io.github.gulliby.stoptable.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gulliby.stoptable.core.Feed;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CheckTest {
    @Test
    void emptyFeedHasNoFindings() {
        assertEquals(List.of(), Check.run(feed(List.of(), List.of(), List.of())));
    }

    @Test
    void reportsUnusedStopsAndServicesInLineThenCodeOrder() {
        Feed value = feed(
                List.of(stop("A", "Alpha", 2), stop("B", "Beta", 1)),
                List.of(service("X", 1, 3), service("Y", 1, 1)),
                List.of());

        assertEquals(List.of(
                finding(1, "TT001", "stop \"B\" is used by no trip"),
                finding(1, "TT002", "service \"Y\" is used by no trip"),
                finding(2, "TT001", "stop \"A\" is used by no trip"),
                finding(3, "TT002", "service \"X\" is used by no trip")),
                Check.run(value));
    }

    @Test
    void reportsNoUnusedEntitiesWhenStopsAndServicesAreUsed() {
        Feed value = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2)),
                List.of(service("WD", 31, 3)),
                List.of(trip("R", "WD", List.of(call("A", 480), call("B", 490)), 4)));

        assertEquals(List.of(), Check.run(value));
    }

    @Test
    void exactTripRepeatsReferenceTheFirstEarlierTripInFeedOrder() {
        Feed.Trip first = trip("R", "WD", List.of(call("A", 480), call("B", 490)), 30);
        Feed.Trip second = trip("R", "WD", List.of(call("A", 480), call("B", 490)), 10);
        Feed.Trip third = trip("R", "WD", List.of(call("A", 480), call("B", 490)), 20);
        Feed value = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2)),
                List.of(service("WD", 31, 3)),
                List.of(first, second, third));

        assertEquals(List.of(
                finding(10, "TT003", "trip repeats line 30"),
                finding(20, "TT003", "trip repeats line 30")),
                Check.run(value));
    }

    @Test
    void repeatEqualityIncludesRouteServiceAndEveryCall() {
        Feed value = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2), stop("C", "Charlie", 3)),
                List.of(service("WD", 31, 4), service("WE", 96, 5)),
                List.of(
                        trip("R", "WD", List.of(call("A", 480), call("B", 490)), 10),
                        trip("S", "WD", List.of(call("A", 480), call("B", 490)), 11),
                        trip("R", "WE", List.of(call("A", 480), call("B", 490)), 12),
                        trip("R", "WD", List.of(call("A", 480), call("C", 490)), 13),
                        trip("R", "WD", List.of(call("A", 481), call("B", 491)), 14),
                        trip("R", "WD", List.of(call("A", 480), call("C", 490)), 15)));

        assertEquals(List.of(
                finding(13, "TT004",
                        "route R also leaves stop \"A\" at 08:00 on a shared day (line 10)"),
                finding(15, "TT003", "trip repeats line 13"),
                finding(15, "TT004",
                        "route R also leaves stop \"A\" at 08:00 on a shared day (line 10)")),
                Check.run(value));
    }

    @Test
    void reportsFirstEarlierOverlappingDepartureForTt004() {
        Feed value = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2), stop("C", "Charlie", 3)),
                List.of(service("WD", 31, 4), service("ALL", 127, 5)),
                List.of(
                        trip("R", "WD", List.of(call("A", 480), call("B", 490)), 10),
                        trip("R", "ALL", List.of(call("A", 480), call("C", 491)), 11),
                        trip("R", "WD", List.of(call("A", 480), call("C", 492)), 12)));

        assertEquals(List.of(
                finding(11, "TT004",
                        "route R also leaves stop \"A\" at 08:00 on a shared day (line 10)"),
                finding(12, "TT004",
                        "route R also leaves stop \"A\" at 08:00 on a shared day (line 10)")),
                Check.run(value));
    }

    @Test
    void tt003AndTt004CanBothBeReportedForOneTripWithCandidateSpecificExclusion() {
        Feed value = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2), stop("C", "Charlie", 3)),
                List.of(service("WD", 31, 4), service("WE", 96, 5)),
                List.of(
                        trip("R", "WD", List.of(call("A", 480), call("B", 490)), 10),
                        trip("R", "WD", List.of(call("A", 480), call("C", 491)), 11),
                        trip("R", "WE", List.of(call("A", 480), call("B", 490)), 12),
                        trip("R", "WD", List.of(call("A", 480), call("B", 490)), 13)));

        assertEquals(List.of(
                finding(11, "TT004",
                        "route R also leaves stop \"A\" at 08:00 on a shared day (line 10)"),
                finding(13, "TT003", "trip repeats line 10"),
                finding(13, "TT004",
                        "route R also leaves stop \"A\" at 08:00 on a shared day (line 11)")),
                Check.run(value));
    }

    @ParameterizedTest
    @MethodSource("nonConflictingDepartures")
    void differentRouteOriginTimeOrDisjointDaysDoNotConflict(
            Feed.Trip first, Feed.Trip second, int firstDays, int secondDays) {
        Feed value = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2), stop("C", "Charlie", 3)),
                List.of(service("ONE", firstDays, 4), service("TWO", secondDays, 5)),
                List.of(first, second));

        assertEquals(List.of(), Check.run(value));
    }

    private static Stream<Arguments> nonConflictingDepartures() {
        return Stream.of(
                Arguments.of(
                        trip("R", "ONE", List.of(call("A", 480), call("B", 490)), 10),
                        trip("S", "TWO", List.of(call("A", 480), call("C", 490)), 11),
                        31, 31),
                Arguments.of(
                        trip("R", "ONE", List.of(call("A", 480), call("B", 490)), 10),
                        trip("R", "TWO", List.of(call("B", 480), call("C", 490)), 11),
                        31, 31),
                Arguments.of(
                        trip("R", "ONE", List.of(call("A", 480), call("B", 490)), 10),
                        trip("R", "TWO", List.of(call("A", 481), call("C", 491)), 11),
                        31, 31),
                Arguments.of(
                        trip("R", "ONE", List.of(call("A", 480), call("B", 490)), 10),
                        trip("R", "TWO", List.of(call("A", 480), call("C", 491)), 11),
                        31, 96));
    }

    @Test
    void reportsEveryOtherUsedGroupUsingItsFirstDeclaredStopAndIgnoresUnusedStops() {
        Feed value = feed(
                List.of(
                        stop("U", "Unused", 1),
                        stop("A", "Alpha", 2),
                        stop("B", "Beta", 3),
                        stop("C", "Charlie", 4),
                        stop("D", "Delta", 5),
                        stop("E", "Echo", 6),
                        stop("F", "Foxtrot", 7),
                        stop("G", "Golf", 8)),
                List.of(service("WD", 31, 9)),
                List.of(
                        trip("R1", "WD", List.of(call("C", 520), call("B", 530)), 10),
                        trip("R2", "WD", List.of(call("B", 540), call("A", 550)), 11),
                        trip("R3", "WD", List.of(call("E", 560), call("D", 570)), 12),
                        trip("R4", "WD", List.of(call("F", 580), call("G", 590)), 13)));

        assertEquals(List.of(
                finding(1, "TT001", "stop \"U\" is used by no trip"),
                finding(5, "TT005", "stop \"D\" is not connected to stop \"A\""),
                finding(7, "TT005", "stop \"F\" is not connected to stop \"A\"")),
                Check.run(value));
    }

    @Test
    void connectedStopsAreGroupedTransitivelyWithoutRespectingTripDirection() {
        Feed value = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2), stop("C", "Charlie", 3)),
                List.of(service("WD", 31, 4)),
                List.of(
                        trip("R1", "WD", List.of(call("C", 520), call("B", 530)), 10),
                        trip("R2", "WD", List.of(call("B", 540), call("A", 550)), 11)));

        assertEquals(List.of(), Check.run(value));
    }

    @Test
    void unusedStopsDoNotBecomeDisconnectedGroupsAndNoUsedStopsMeansNoTt005() {
        Feed value = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2), stop("C", "Charlie", 3)),
                List.of(service("WD", 31, 4)),
                List.of(trip("R", "WD", List.of(call("A", 480), call("B", 490)), 5)));

        assertEquals(List.of(finding(3, "TT001", "stop \"C\" is used by no trip")), Check.run(value));
        assertEquals(List.of(
                finding(1, "TT001", "stop \"A\" is used by no trip"),
                finding(2, "TT001", "stop \"B\" is used by no trip"),
                finding(4, "TT002", "service \"WD\" is used by no trip")),
                Check.run(feed(
                        List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2)),
                        List.of(service("WD", 31, 4)),
                        List.of())));
    }

    @Test
    void findingsOnTheSameLineAreOrderedByCode() {
        Feed value = feed(
                List.of(
                        stop("A", "Alpha", 1),
                        stop("B", "Beta", 2),
                        stop("C", "Charlie", 5),
                        stop("D", "Delta", 6)),
                List.of(service("WD", 31, 3), service("UNUSED", 31, 5)),
                List.of(
                        trip("R", "WD", List.of(call("A", 480), call("B", 490)), 10),
                        trip("S", "WD", List.of(call("C", 500), call("D", 510)), 11)));

        assertEquals(List.of(
                finding(5, "TT002", "service \"UNUSED\" is used by no trip"),
                finding(5, "TT005", "stop \"C\" is not connected to stop \"A\"")),
                Check.run(value));
    }

    @Test
    void messyFixtureProducesAllSixSpecifiedFindingsInExactOrder() {
        Feed value = feed(
                List.of(
                        stop("A", "Alpha", 1),
                        stop("B", "Beta", 2),
                        stop("C", "Charlie", 3),
                        stop("D", "Delta", 4),
                        stop("E", "Echo", 5),
                        stop("F", "Foxtrot", 6)),
                List.of(
                        service("WD", 31, 7),
                        service("WE", 96, 8),
                        service("XX", 1, 9)),
                List.of(
                        trip("1", "WD", List.of(call("A", 480), call("B", 490)), 10),
                        trip("1", "WD", List.of(call("A", 480), call("B", 490)), 11),
                        trip("1", "XX", List.of(call("A", 480), call("B", 492)), 12),
                        trip("2", "WD", List.of(call("E", 540), call("F", 545)), 13)));

        assertEquals(List.of(
                finding(3, "TT001", "stop \"C\" is used by no trip"),
                finding(4, "TT001", "stop \"D\" is used by no trip"),
                finding(5, "TT005", "stop \"E\" is not connected to stop \"A\""),
                finding(8, "TT002", "service \"WE\" is used by no trip"),
                finding(11, "TT003", "trip repeats line 10"),
                finding(12, "TT004",
                        "route 1 also leaves stop \"A\" at 08:00 on a shared day (line 10)")),
                Check.run(value));
    }

    @Test
    void villageFixtureHasNoFindings() {
        Feed value = feed(
                List.of(
                        stop("HAR", "Harbor Pier", 2),
                        stop("MKT", "Market Square", 3),
                        stop("CHU", "Church Lane", 4),
                        stop("PRK", "Park Gate", 5),
                        stop("SCH", "School", 6)),
                List.of(service("WD", 31, 7), service("WE", 96, 8)),
                List.of(
                        trip("12", "WD", List.of(
                                call("HAR", 420), call("MKT", 430), call("CHU", 438), call("PRK", 450)), 10),
                        trip("12", "WD", List.of(
                                call("HAR", 480), call("MKT", 490), call("CHU", 498), call("PRK", 510)), 11),
                        trip("14", "WD", List.of(
                                call("MKT", 432), call("SCH", 455), call("PRK", 470)), 12),
                        trip("14", "WD", List.of(
                                call("MKT", 495), call("SCH", 515), call("PRK", 530)), 13),
                        trip("20", "WE", List.of(
                                call("HAR", 540), call("MKT", 552), call("PRK", 580)), 14)));

        assertEquals(List.of(), Check.run(value));
    }

    private static Feed feed(
            List<Feed.Stop> stops, List<Feed.Service> services, List<Feed.Trip> trips) {
        return new Feed(stops, services, trips);
    }

    private static Feed.Stop stop(String id, String name, int line) {
        return new Feed.Stop(id, name, line);
    }

    private static Feed.Service service(String id, int days, int line) {
        return new Feed.Service(id, days, line);
    }

    private static Feed.Call call(String stop, int minutes) {
        return new Feed.Call(stop, minutes);
    }

    private static Feed.Trip trip(String route, String service, List<Feed.Call> calls, int line) {
        return new Feed.Trip(route, service, calls, line);
    }

    private static Check.Finding finding(int line, String code, String message) {
        return new Check.Finding(line, code, message);
    }
}
