package io.github.gulliby.stoptable.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gulliby.stoptable.core.Feed;

import java.util.List;

import org.junit.jupiter.api.Test;

class BoardTest {
    @Test
    void villageMarketBoardMatchesTheSuppliedExample() {
        Feed feed = villageFeed();

        assertEquals(List.of(
                row(432, "14", "Park Gate"),
                row(490, "12", "Park Gate"),
                row(495, "14", "Park Gate")),
                Board.rows(feed, "MKT", 0, 431));
    }

    @Test
    void inactiveWeekendQueryHasNoVillageSchoolDeparture() {
        assertEquals(List.of(), Board.rows(villageFeed(), "SCH", 5, 0));
    }

    @Test
    void departureAtAfterIsIncludedAndEarlierDepartureIsExcluded() {
        Feed feed = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2)),
                List.of(service("WD", 31, 3)),
                List.of(trip("R", "WD", List.of(call("A", 480), call("B", 490)), 4)));

        assertEquals(List.of(row(480, "R", "Beta")), Board.rows(feed, "A", 0, 480));
        assertEquals(List.of(), Board.rows(feed, "A", 0, 481));
    }

    @Test
    void intermediateCallIsADepartureToTheTripFinalStopName() {
        Feed feed = feed(
                List.of(
                        stop("A", "Alpha", 1),
                        stop("B", "Market Square", 2),
                        stop("C", "Church Lane", 3)),
                List.of(service("WD", 31, 4)),
                List.of(trip("R", "WD", List.of(
                        call("A", 420),
                        call("B", 430),
                        call("C", 440)), 5)));

        assertEquals(List.of(row(430, "R", "Church Lane")), Board.rows(feed, "B", 0, 0));
    }

    @Test
    void terminalCallNeverAppearsAsADeparture() {
        Feed feed = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2)),
                List.of(service("WD", 31, 3)),
                List.of(trip("R", "WD", List.of(call("A", 480), call("B", 490)), 4)));

        assertEquals(List.of(), Board.rows(feed, "B", 0, 0));
    }

    @Test
    void serviceDayMaskSelectsOnlyTripsRunningOnTheRequestedDay() {
        Feed feed = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2), stop("C", "Charlie", 3)),
                List.of(service("WD", 31, 4), service("WE", 96, 5), service("NONE", 0, 6)),
                List.of(
                        trip("W", "WD", List.of(call("A", 480), call("B", 490)), 7),
                        trip("E", "WE", List.of(call("A", 500), call("C", 510)), 8),
                        trip("N", "NONE", List.of(call("A", 520), call("B", 530)), 9)));

        assertEquals(List.of(row(480, "W", "Beta")), Board.rows(feed, "A", 0, 0));
        assertEquals(List.of(row(500, "E", "Charlie")), Board.rows(feed, "A", 5, 0));
    }

    @Test
    void sundayServiceRunsOnSundayButNotSaturday() {
        Feed feed = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2)),
                List.of(service("SUN", 64, 3)),
                List.of(trip("R", "SUN", List.of(call("A", 600), call("B", 610)), 4)));

        assertEquals(List.of(), Board.rows(feed, "A", 5, 0));
        assertEquals(List.of(row(600, "R", "Beta")), Board.rows(feed, "A", 6, 0));
    }

    @Test
    void ordersByTimeThenAsciiRouteRatherThanNumericRouteValue() {
        Feed feed = feed(
                List.of(
                        stop("A", "Alpha", 1),
                        stop("B", "Beta", 2),
                        stop("C", "Charlie", 3),
                        stop("D", "Delta", 4)),
                List.of(service("WD", 31, 5)),
                List.of(
                        trip("Z", "WD", List.of(call("A", 419), call("D", 429)), 6),
                        trip("2", "WD", List.of(call("A", 420), call("B", 430)), 7),
                        trip("10", "WD", List.of(call("A", 420), call("C", 430)), 8)));

        assertEquals(List.of(
                row(419, "Z", "Delta"),
                row(420, "10", "Charlie"),
                row(420, "2", "Beta")),
                Board.rows(feed, "A", 0, 0));
    }

    @Test
    void equalTimeAndRouteTiesUseTripPositionInFeedOrder() {
        Feed feed = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "First Destination", 2), stop("C", "Second Destination", 3)),
                List.of(service("WD", 31, 4)),
                List.of(
                        trip("R", "WD", List.of(call("A", 480), call("B", 490)), 5),
                        trip("R", "WD", List.of(call("A", 480), call("C", 490)), 6)));

        assertEquals(List.of(
                row(480, "R", "First Destination"),
                row(480, "R", "Second Destination")),
                Board.rows(feed, "A", 0, 0));
    }

    @Test
    void identicalEligibleRowsAreRetained() {
        Feed.Trip trip = trip("R", "WD", List.of(call("A", 480), call("B", 490)), 4);
        Feed feed = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2)),
                List.of(service("WD", 31, 3)),
                List.of(trip, trip));

        assertEquals(List.of(row(480, "R", "Beta"), row(480, "R", "Beta")),
                Board.rows(feed, "A", 0, 0));
    }

    @Test
    void returnsEveryEligibleDepartureWhenThereAreMoreThanTen() {
        Feed feed = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2)),
                List.of(service("WD", 31, 3)),
                List.of(
                        trip("T00", "WD", List.of(call("A", 480), call("B", 490)), 4),
                        trip("T01", "WD", List.of(call("A", 480), call("B", 490)), 5),
                        trip("T02", "WD", List.of(call("A", 480), call("B", 490)), 6),
                        trip("T03", "WD", List.of(call("A", 480), call("B", 490)), 7),
                        trip("T04", "WD", List.of(call("A", 480), call("B", 490)), 8),
                        trip("T05", "WD", List.of(call("A", 480), call("B", 490)), 9),
                        trip("T06", "WD", List.of(call("A", 480), call("B", 490)), 10),
                        trip("T07", "WD", List.of(call("A", 480), call("B", 490)), 11),
                        trip("T08", "WD", List.of(call("A", 480), call("B", 490)), 12),
                        trip("T09", "WD", List.of(call("A", 480), call("B", 490)), 13),
                        trip("T10", "WD", List.of(call("A", 480), call("B", 490)), 14)));

        assertEquals(List.of(
                row(480, "T00", "Beta"),
                row(480, "T01", "Beta"),
                row(480, "T02", "Beta"),
                row(480, "T03", "Beta"),
                row(480, "T04", "Beta"),
                row(480, "T05", "Beta"),
                row(480, "T06", "Beta"),
                row(480, "T07", "Beta"),
                row(480, "T08", "Beta"),
                row(480, "T09", "Beta"),
                row(480, "T10", "Beta")),
                Board.rows(feed, "A", 0, 0));
    }

    @Test
    void emptyFeedProducesNoRows() {
        assertEquals(List.of(), Board.rows(feed(List.of(), List.of(), List.of()), "A", 0, 0));
    }

    @Test
    void unmatchedStopProducesNoRows() {
        Feed feed = feed(
                List.of(stop("A", "Alpha", 1), stop("B", "Beta", 2)),
                List.of(service("WD", 31, 3)),
                List.of(trip("R", "WD", List.of(call("A", 480), call("B", 490)), 4)));

        assertEquals(List.of(), Board.rows(feed, "C", 0, 0));
    }

    private static Feed villageFeed() {
        return feed(
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
    }

    private static Feed feed(List<Feed.Stop> stops, List<Feed.Service> services, List<Feed.Trip> trips) {
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

    private static Board.Row row(int minutes, String route, String to) {
        return new Board.Row(minutes, route, to);
    }
}
