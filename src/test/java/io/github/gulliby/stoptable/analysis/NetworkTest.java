package io.github.gulliby.stoptable.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.stoptable.core.Feed;

import java.util.List;

import org.junit.jupiter.api.Test;

class NetworkTest {
    @Test
    void emptyFeedHasExactDotAndMermaidOutput() {
        Feed feed = feed(List.of(), List.of(), List.of());

        assertEquals(List.of("digraph stoptable {", "  rankdir=LR;", "}"), Network.dot(feed));
        assertEquals(List.of("graph LR"), Network.mermaid(feed));
    }

    @Test
    void unusedStopsAndServicesAreOmitted() {
        Feed feed = feed(
                List.of(stop("A", "Unused Alpha"), stop("B", "Unused Beta")),
                List.of(service("WD", 31)),
                List.of());

        assertEquals(List.of("digraph stoptable {", "  rankdir=LR;", "}"), Network.dot(feed));
        assertEquals(List.of("graph LR"), Network.mermaid(feed));
    }

    @Test
    void tieFixtureMatchesTheSuppliedDotAndMermaidExamples() {
        Feed feed = feed(
                List.of(stop("A", "Start"), stop("M", "Mid"), stop("Z", "End")),
                List.of(service("WD", 31)),
                List.of(
                        trip("7", "WD", call("A", 430), call("Z", 450)),
                        trip("5", "WD", call("A", 420), call("M", 425)),
                        trip("6", "WD", call("M", 428), call("Z", 450))));

        assertEquals(List.of(
                "digraph stoptable {",
                "  rankdir=LR;",
                "  \"A\" [label=\"Start\"];",
                "  \"M\" [label=\"Mid\"];",
                "  \"Z\" [label=\"End\"];",
                "  \"A\" -> \"M\" [label=\"5\"];",
                "  \"A\" -> \"Z\" [label=\"7\"];",
                "  \"M\" -> \"Z\" [label=\"6\"];",
                "}"), Network.dot(feed));
        assertEquals(List.of(
                "graph LR",
                "  A[\"Start\"]",
                "  M[\"Mid\"]",
                "  Z[\"End\"]",
                "  A -->|5| M",
                "  A -->|7| Z",
                "  M -->|6| Z"), Network.mermaid(feed));
    }

    @Test
    void repeatedDirectedPairHasOneEdgeWithDistinctAsciiSortedRoutes() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("B", "Beta")),
                List.of(service("WD", 31)),
                List.of(
                        trip("z", "WD", call("A", 420), call("B", 430)),
                        trip("A", "WD", call("A", 421), call("B", 431)),
                        trip("Z", "WD", call("A", 422), call("B", 432)),
                        trip("A", "WD", call("A", 423), call("B", 433))));

        assertEquals(List.of(
                "digraph stoptable {",
                "  rankdir=LR;",
                "  \"A\" [label=\"Alpha\"];",
                "  \"B\" [label=\"Beta\"];",
                "  \"A\" -> \"B\" [label=\"A,Z,z\"];",
                "}"), Network.dot(feed));
        assertEquals(List.of(
                "graph LR",
                "  A[\"Alpha\"]",
                "  B[\"Beta\"]",
                "  A -->|A,Z,z| B"), Network.mermaid(feed));
    }

    @Test
    void reverseDirectedPairsRemainSeparateAndAreOrderedByDeclarationPosition() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("B", "Beta")),
                List.of(service("WD", 31)),
                List.of(
                        trip("BACK", "WD", call("B", 430), call("A", 440)),
                        trip("FORWARD", "WD", call("A", 420), call("B", 430))));

        assertEquals(List.of(
                "digraph stoptable {",
                "  rankdir=LR;",
                "  \"A\" [label=\"Alpha\"];",
                "  \"B\" [label=\"Beta\"];",
                "  \"A\" -> \"B\" [label=\"FORWARD\"];",
                "  \"B\" -> \"A\" [label=\"BACK\"];",
                "}"), Network.dot(feed));
        assertEquals(List.of(
                "graph LR",
                "  A[\"Alpha\"]",
                "  B[\"Beta\"]",
                "  A -->|FORWARD| B",
                "  B -->|BACK| A"), Network.mermaid(feed));
    }

    @Test
    void nodeAndEdgeOrderUsesStopDeclarationIndicesNotTripOrder() {
        Feed feed = feed(
                List.of(
                        stop("C", "Charlie"),
                        stop("A", "Alpha"),
                        stop("B", "Beta"),
                        stop("D", "Delta")),
                List.of(service("WD", 31)),
                List.of(
                        trip("1", "WD", call("B", 420), call("D", 430)),
                        trip("2", "WD", call("C", 421), call("D", 431)),
                        trip("3", "WD", call("B", 422), call("A", 432)),
                        trip("4", "WD", call("C", 423), call("A", 433))));

        assertEquals(List.of(
                "digraph stoptable {",
                "  rankdir=LR;",
                "  \"C\" [label=\"Charlie\"];",
                "  \"A\" [label=\"Alpha\"];",
                "  \"B\" [label=\"Beta\"];",
                "  \"D\" [label=\"Delta\"];",
                "  \"C\" -> \"A\" [label=\"4\"];",
                "  \"C\" -> \"D\" [label=\"2\"];",
                "  \"B\" -> \"A\" [label=\"3\"];",
                "  \"B\" -> \"D\" [label=\"1\"];",
                "}"), Network.dot(feed));
        assertEquals(List.of(
                "graph LR",
                "  C[\"Charlie\"]",
                "  A[\"Alpha\"]",
                "  B[\"Beta\"]",
                "  D[\"Delta\"]",
                "  C -->|4| A",
                "  C -->|2| D",
                "  B -->|3| A",
                "  B -->|1| D"), Network.mermaid(feed));
    }

    @Test
    void tripsOnEachOfTheSevenServiceDaysAreIncluded() {
        Feed feed = feed(
                List.of(
                        stop("A", "A"), stop("B", "B"),
                        stop("C", "C"), stop("D", "D"),
                        stop("E", "E"), stop("F", "F"),
                        stop("G", "G"), stop("H", "H")),
                List.of(
                        service("M", 1), service("T", 2), service("W", 4),
                        service("H", 8), service("F", 16), service("S", 32), service("U", 64)),
                List.of(
                        trip("1", "M", call("A", 420), call("B", 430)),
                        trip("2", "T", call("B", 420), call("C", 430)),
                        trip("3", "W", call("C", 420), call("D", 430)),
                        trip("4", "H", call("D", 420), call("E", 430)),
                        trip("5", "F", call("E", 420), call("F", 430)),
                        trip("6", "S", call("F", 420), call("G", 430)),
                        trip("7", "U", call("G", 420), call("H", 430))));

        assertEquals(List.of(
                "digraph stoptable {",
                "  rankdir=LR;",
                "  \"A\" [label=\"A\"];",
                "  \"B\" [label=\"B\"];",
                "  \"C\" [label=\"C\"];",
                "  \"D\" [label=\"D\"];",
                "  \"E\" [label=\"E\"];",
                "  \"F\" [label=\"F\"];",
                "  \"G\" [label=\"G\"];",
                "  \"H\" [label=\"H\"];",
                "  \"A\" -> \"B\" [label=\"1\"];",
                "  \"B\" -> \"C\" [label=\"2\"];",
                "  \"C\" -> \"D\" [label=\"3\"];",
                "  \"D\" -> \"E\" [label=\"4\"];",
                "  \"E\" -> \"F\" [label=\"5\"];",
                "  \"F\" -> \"G\" [label=\"6\"];",
                "  \"G\" -> \"H\" [label=\"7\"];",
                "}"), Network.dot(feed));
        assertEquals(List.of(
                "graph LR",
                "  A[\"A\"]", "  B[\"B\"]", "  C[\"C\"]", "  D[\"D\"]",
                "  E[\"E\"]", "  F[\"F\"]", "  G[\"G\"]", "  H[\"H\"]",
                "  A -->|1| B", "  B -->|2| C", "  C -->|3| D", "  D -->|4| E",
                "  E -->|5| F", "  F -->|6| G", "  G -->|7| H"), Network.mermaid(feed));
    }

    @Test
    void allowedPunctuationInStopNamesIsPreservedInBothFormats() {
        Feed feed = feed(
                List.of(
                        stop("A", "St. John's / Market & Pier (Old)"),
                        stop("B", "Harbor-Gate, East")),
                List.of(service("WD", 31)),
                List.of(trip("R", "WD", call("A", 420), call("B", 430))));

        assertEquals(List.of(
                "digraph stoptable {",
                "  rankdir=LR;",
                "  \"A\" [label=\"St. John's / Market & Pier (Old)\"];",
                "  \"B\" [label=\"Harbor-Gate, East\"];",
                "  \"A\" -> \"B\" [label=\"R\"];",
                "}"), Network.dot(feed));
        assertEquals(List.of(
                "graph LR",
                "  A[\"St. John's / Market & Pier (Old)\"]",
                "  B[\"Harbor-Gate, East\"]",
                "  A -->|R| B"), Network.mermaid(feed));
    }

    @Test
    void multiCallTripAddsOnlyConsecutiveCallEdges() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("M", "Mid"), stop("Z", "Zulu")),
                List.of(service("WD", 31)),
                List.of(trip(
                        "R", "WD",
                        call("A", 420), call("M", 430), call("Z", 440))));

        assertEquals(List.of(
                "digraph stoptable {",
                "  rankdir=LR;",
                "  \"A\" [label=\"Alpha\"];",
                "  \"M\" [label=\"Mid\"];",
                "  \"Z\" [label=\"Zulu\"];",
                "  \"A\" -> \"M\" [label=\"R\"];",
                "  \"M\" -> \"Z\" [label=\"R\"];",
                "}"), Network.dot(feed));
        assertEquals(List.of(
                "graph LR",
                "  A[\"Alpha\"]",
                "  M[\"Mid\"]",
                "  Z[\"Zulu\"]",
                "  A -->|R| M",
                "  M -->|R| Z"), Network.mermaid(feed));
    }

    @Test
    void returnedLinesContainNoCarriageReturnsOrLineFeeds() {
        Feed feed = feed(
                List.of(stop("A", "Alpha"), stop("B", "Beta")),
                List.of(service("WD", 31)),
                List.of(trip("R", "WD", call("A", 420), call("B", 430))));

        assertTrue(Network.dot(feed).stream()
                .allMatch(line -> !line.contains("\r") && !line.contains("\n")));
        assertTrue(Network.mermaid(feed).stream()
                .allMatch(line -> !line.contains("\r") && !line.contains("\n")));
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

    private static Feed.Trip trip(String route, String service, Feed.Call... calls) {
        return new Feed.Trip(route, service, List.of(calls), 1);
    }
}
