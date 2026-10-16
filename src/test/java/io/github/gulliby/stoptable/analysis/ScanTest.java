package io.github.gulliby.stoptable.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gulliby.stoptable.core.Feed;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ScanTest {
    @Test
    void villageJourneyWithNoTransferMatchesTheSuppliedExample() {
        assertEquals(Optional.of(List.of(
                leg("12", "HAR", 420, "MKT", 430),
                leg("14", "MKT", 432, "SCH", 455))),
                Scan.journey(villageFeed(), "HAR", "SCH", 0, 390, 0));
    }

    @Test
    void villageJourneyWithThreeMinuteTransferUsesTheLaterBus() {
        assertEquals(Optional.of(List.of(
                leg("12", "HAR", 420, "MKT", 430),
                leg("14", "MKT", 495, "SCH", 515))),
                Scan.journey(villageFeed(), "HAR", "SCH", 0, 390, 3));
    }

    @Test
    void villageJourneyInReverseDirectionIsUnreachable() {
        assertEquals(Optional.empty(), Scan.journey(villageFeed(), "SCH", "HAR", 0, 0, 0));
    }

    @Test
    void equalArrivalKeepsTheAlternativeJourneyFromTheSuppliedTieExample() {
        Feed feed = feed(
                List.of(stop("A", "Start"), stop("M", "Mid"), stop("Z", "End")),
                List.of(service("WD", 31)),
                List.of(
                        trip("7", "WD", List.of(call("A", 430), call("Z", 450))),
                        trip("5", "WD", List.of(call("A", 420), call("M", 425))),
                        trip("6", "WD", List.of(call("M", 428), call("Z", 450)))));

        assertEquals(Optional.of(List.of(
                leg("5", "A", 420, "M", 425),
                leg("6", "M", 428, "Z", 450))),
                Scan.journey(feed, "A", "Z", 0, 390, 0));
    }

    @Test
    void firstDepartureAtAfterIsAccepted() {
        Feed feed = directFeed(420, 430);

        assertEquals(Optional.of(List.of(leg("R", "A", 420, "B", 430))),
                Scan.journey(feed, "A", "B", 0, 420, 0));
    }

    @Test
    void firstDepartureOneMinuteBeforeAfterIsMissed() {
        Feed feed = directFeed(420, 430);

        assertEquals(Optional.empty(), Scan.journey(feed, "A", "B", 0, 421, 0));
    }

    @Test
    void zeroTransferAllowsAConnectionAtTheArrivalMinute() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("M", "Mid"), stop("Z", "Zulu")),
                List.of(service("WD", 31)),
                List.of(
                        trip("1", "WD", List.of(call("A", 420), call("M", 430))),
                        trip("2", "WD", List.of(call("M", 430), call("Z", 440)))));

        assertEquals(Optional.of(List.of(
                leg("1", "A", 420, "M", 430),
                leg("2", "M", 430, "Z", 440))),
                Scan.journey(feed, "A", "Z", 0, 420, 0));
    }

    @Test
    void transferAtTheMaximumOfOneHundredTwentyMinutesIsAccepted() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("M", "Mid"), stop("Z", "Zulu")),
                List.of(service("WD", 31)),
                List.of(
                        trip("1", "WD", List.of(call("A", 420), call("M", 430))),
                        trip("2", "WD", List.of(call("M", 550), call("Z", 560)))));

        assertEquals(Optional.of(List.of(
                leg("1", "A", 420, "M", 430),
                leg("2", "M", 550, "Z", 560))),
                Scan.journey(feed, "A", "Z", 0, 420, 120));
    }

    @Test
    void transferOneMinuteShortOfTheMaximumIsMissed() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("M", "Mid"), stop("Z", "Zulu")),
                List.of(service("WD", 31)),
                List.of(
                        trip("1", "WD", List.of(call("A", 420), call("M", 430))),
                        trip("2", "WD", List.of(call("M", 549), call("Z", 560)))));

        assertEquals(Optional.empty(), Scan.journey(feed, "A", "Z", 0, 420, 120));
    }

    @Test
    void stayingAboardDoesNotRequireTransferTime() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("M", "Mid"), stop("Z", "Zulu")),
                List.of(service("WD", 31)),
                List.of(trip("R", "WD", List.of(
                        call("A", 420), call("M", 430), call("Z", 440)))));

        assertEquals(Optional.of(List.of(leg("R", "A", 420, "Z", 440))),
                Scan.journey(feed, "A", "Z", 0, 420, 120));
    }

    @Test
    void boardingAtAnIntermediateCallRetainsThatFirstBoardingIndex() {
        Feed feed = feed(
                List.of(
                        stop("A", "Alpha"),
                        stop("M", "Market"),
                        stop("N", "North"),
                        stop("Z", "Zulu")),
                List.of(service("WD", 31)),
                List.of(trip("R", "WD", List.of(
                        call("A", 420), call("M", 430), call("N", 440), call("Z", 450)))));

        assertEquals(Optional.of(List.of(leg("R", "M", 430, "Z", 450))),
                Scan.journey(feed, "M", "Z", 0, 430, 120));
    }

    @Test
    void aStrictlyEarlierArrivalReplacesThePreviouslyBestArrival() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("Z", "Zulu")),
                List.of(service("WD", 31)),
                List.of(
                        trip("LATE", "WD", List.of(call("A", 420), call("Z", 480))),
                        trip("EARLY", "WD", List.of(call("A", 425), call("Z", 470)))));

        assertEquals(Optional.of(List.of(leg("EARLY", "A", 425, "Z", 470))),
                Scan.journey(feed, "A", "Z", 0, 420, 0));
    }

    @Test
    void anEqualArrivalDoesNotReplaceTheEarlierScannedConnection() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("M", "Mid"), stop("Z", "Zulu")),
                List.of(service("WD", 31)),
                List.of(
                        trip("DIRECT", "WD", List.of(call("A", 420), call("Z", 450))),
                        trip("FIRST", "WD", List.of(call("A", 420), call("M", 425))),
                        trip("SECOND", "WD", List.of(call("M", 430), call("Z", 450)))));

        assertEquals(Optional.of(List.of(leg("DIRECT", "A", 420, "Z", 450))),
                Scan.journey(feed, "A", "Z", 0, 420, 0));
    }

    @Test
    void equalDepartureAndArrivalUseTheEarlierTripPosition() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("M", "Mid"), stop("Z", "Zulu")),
                List.of(service("WD", 31)),
                List.of(
                        trip("9", "WD", List.of(call("A", 420), call("M", 430))),
                        trip("1", "WD", List.of(call("A", 420), call("M", 430))),
                        trip("3", "WD", List.of(call("M", 431), call("Z", 440)))));

        assertEquals(Optional.of(List.of(
                leg("9", "A", 420, "M", 430),
                leg("3", "M", 431, "Z", 440))),
                Scan.journey(feed, "A", "Z", 0, 420, 0));
    }

    @Test
    void tripOnAnInactiveServiceDoesNotCreateAConnection() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("B", "Beta")),
                List.of(service("MON", 1)),
                List.of(trip("R", "MON", List.of(call("A", 420), call("B", 430)))));

        assertEquals(Optional.empty(), Scan.journey(feed, "A", "B", 1, 0, 0));
    }

    @Test
    void emptyFeedHasNoJourney() {
        assertEquals(Optional.empty(),
                Scan.journey(feed(List.of(), List.of(), List.of()), "A", "B", 0, 0, 0));
    }

    @Test
    void missedTransferLeavesTheDestinationUnreachable() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("M", "Mid"), stop("Z", "Zulu")),
                List.of(service("WD", 31)),
                List.of(
                        trip("1", "WD", List.of(call("A", 420), call("M", 430))),
                        trip("2", "WD", List.of(call("M", 432), call("Z", 440)))));

        assertEquals(Optional.empty(), Scan.journey(feed, "A", "Z", 0, 420, 3));
    }

    @Test
    void reconstructsThreeLegsInForwardOrder() {
        Feed feed = feed(
                List.of(
                        stop("A", "Alpha"),
                        stop("B", "Beta"),
                        stop("C", "Charlie"),
                        stop("D", "Delta")),
                List.of(service("WD", 31)),
                List.of(
                        trip("1", "WD", List.of(call("A", 420), call("B", 430))),
                        trip("2", "WD", List.of(call("B", 435), call("C", 445))),
                        trip("3", "WD", List.of(call("C", 450), call("D", 460)))));

        assertEquals(Optional.of(List.of(
                leg("1", "A", 420, "B", 430),
                leg("2", "B", 435, "C", 445),
                leg("3", "C", 450, "D", 460))),
                Scan.journey(feed, "A", "D", 0, 420, 5));
    }

    private static Feed villageFeed() {
        return feed(
                List.of(
                        stop("HAR", "Harbor Pier"),
                        stop("MKT", "Market Square"),
                        stop("CHU", "Church Lane"),
                        stop("PRK", "Park Gate"),
                        stop("SCH", "School")),
                List.of(service("WD", 31), service("WE", 96)),
                List.of(
                        trip("12", "WD", List.of(
                                call("HAR", 420), call("MKT", 430), call("CHU", 438), call("PRK", 450))),
                        trip("12", "WD", List.of(
                                call("HAR", 480), call("MKT", 490), call("CHU", 498), call("PRK", 510))),
                        trip("14", "WD", List.of(
                                call("MKT", 432), call("SCH", 455), call("PRK", 470))),
                        trip("14", "WD", List.of(
                                call("MKT", 495), call("SCH", 515), call("PRK", 530))),
                        trip("20", "WE", List.of(
                                call("HAR", 540), call("MKT", 552), call("PRK", 580)))));
    }

    private static Feed directFeed(int departs, int arrives) {
        return feed(
                List.of(stop("A", "Alpha"), stop("B", "Beta")),
                List.of(service("WD", 31)),
                List.of(trip("R", "WD", List.of(call("A", departs), call("B", arrives)))));
    }

    private static Feed feed(
            List<Feed.Stop> stops, List<Feed.Service> services, List<Feed.Trip> trips) {
        return new Feed(stops, services, trips);
    }

    private static Feed.Stop stop(String id, String name) {
        return new Feed.Stop(id, name, 1);
    }

    private static Feed.Service service(String id, int days) {
        return new Feed.Service(id, days, 1);
    }

    private static Feed.Call call(String stop, int minutes) {
        return new Feed.Call(stop, minutes);
    }

    private static Feed.Trip trip(String route, String service, List<Feed.Call> calls) {
        return new Feed.Trip(route, service, calls, 1);
    }

    private static Scan.Leg leg(String route, String from, int departs, String to, int arrives) {
        return new Scan.Leg(route, from, departs, to, arrives);
    }
}
