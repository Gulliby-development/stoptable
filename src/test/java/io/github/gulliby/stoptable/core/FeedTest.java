package io.github.gulliby.stoptable.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FeedTest {
    @Test
    void exposesTheSevenDayNamesInOrder() {
        assertEquals(List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun"), Feed.DAYS);
    }

    @Test
    void preservesEmptyFeedComponents() {
        Feed feed = new Feed(List.of(), List.of(), List.of());

        assertEquals(List.of(), feed.stops());
        assertEquals(List.of(), feed.services());
        assertEquals(List.of(), feed.trips());
    }

    @Test
    void nestedRecordsPreserveEveryComponentIncludingSourceLines() {
        Feed.Stop stop = new Feed.Stop("MKT", "Market Square", 12);
        Feed.Service service = new Feed.Service("WD", 31, 8);
        Feed.Call call = new Feed.Call("MKT", 425);
        Feed.Trip trip = new Feed.Trip("14", "WD", List.of(call), 20);

        assertEquals("MKT", stop.id());
        assertEquals("Market Square", stop.name());
        assertEquals(12, stop.line());
        assertEquals("WD", service.id());
        assertEquals(31, service.days());
        assertEquals(8, service.line());
        assertEquals("MKT", call.stop());
        assertEquals(425, call.minutes());
        assertEquals("14", trip.route());
        assertEquals("WD", trip.service());
        assertEquals(List.of(call), trip.calls());
        assertEquals(20, trip.line());
    }

    @Test
    void preservesStopServiceTripAndCallOrder() {
        Feed.Stop firstStop = new Feed.Stop("A", "Alpha", 1);
        Feed.Stop secondStop = new Feed.Stop("B", "Beta", 2);
        Feed.Service firstService = new Feed.Service("WD", 31, 3);
        Feed.Service secondService = new Feed.Service("WE", 96, 4);
        Feed.Call firstCall = new Feed.Call("A", 420);
        Feed.Call secondCall = new Feed.Call("B", 430);
        Feed.Trip firstTrip = new Feed.Trip("12", "WD", List.of(firstCall, secondCall), 5);
        Feed.Trip secondTrip = new Feed.Trip("14", "WE", List.of(secondCall, firstCall), 6);
        Feed feed = new Feed(
                List.of(firstStop, secondStop),
                List.of(firstService, secondService),
                List.of(firstTrip, secondTrip));

        assertEquals(List.of(firstStop, secondStop), feed.stops());
        assertEquals(List.of(firstService, secondService), feed.services());
        assertEquals(List.of(firstTrip, secondTrip), feed.trips());
        assertEquals(List.of(firstCall, secondCall), feed.trips().get(0).calls());
        assertEquals(List.of(secondCall, firstCall), feed.trips().get(1).calls());
    }

    @Test
    void findsCaseSensitiveStopAndServiceIds() {
        Feed.Stop stop = new Feed.Stop("A", "Alpha", 1);
        Feed.Service service = new Feed.Service("WD", 31, 2);
        Feed feed = new Feed(List.of(stop), List.of(service), List.of());

        assertSame(stop, feed.stop("A"));
        assertNull(feed.stop("a"));
        assertNull(feed.stop("missing"));
        assertSame(service, feed.service("WD"));
        assertNull(feed.service("wd"));
        assertNull(feed.service("missing"));
    }

    @Test
    void lookupReturnsTheFirstMatchingDuplicate() {
        Feed.Stop firstStop = new Feed.Stop("A", "First", 1);
        Feed.Stop secondStop = new Feed.Stop("A", "Second", 2);
        Feed.Service firstService = new Feed.Service("WD", 1, 3);
        Feed.Service secondService = new Feed.Service("WD", 2, 4);
        Feed feed = new Feed(
                List.of(firstStop, secondStop),
                List.of(firstService, secondService),
                List.of());

        assertSame(firstStop, feed.stop("A"));
        assertSame(firstService, feed.service("WD"));
    }

    @Test
    void zeroDayMaskNeverRunsOnAnyIndexedDay() {
        Feed.Service service = new Feed.Service("NONE", 0, 1);

        assertFalse(service.runsOn(0));
        assertFalse(service.runsOn(1));
        assertFalse(service.runsOn(2));
        assertFalse(service.runsOn(3));
        assertFalse(service.runsOn(4));
        assertFalse(service.runsOn(5));
        assertFalse(service.runsOn(6));
    }

    @ParameterizedTest
    @MethodSource("singleDayMasks")
    void eachSingleDayMaskRunsOnlyOnItsIndexedDay(int mask, int runningDay, int nonRunningDay) {
        Feed.Service service = new Feed.Service("ONE", mask, 1);

        assertTrue(service.runsOn(runningDay));
        assertFalse(service.runsOn(nonRunningDay));
    }

    private static Stream<Arguments> singleDayMasks() {
        return Stream.of(
                Arguments.of(1, 0, 1),
                Arguments.of(2, 1, 0),
                Arguments.of(4, 2, 1),
                Arguments.of(8, 3, 2),
                Arguments.of(16, 4, 3),
                Arguments.of(32, 5, 4),
                Arguments.of(64, 6, 5));
    }

    @Test
    void weekdayMaskRunsMondayThroughFridayOnly() {
        Feed.Service service = new Feed.Service("WD", 31, 1);

        assertTrue(service.runsOn(0));
        assertTrue(service.runsOn(1));
        assertTrue(service.runsOn(2));
        assertTrue(service.runsOn(3));
        assertTrue(service.runsOn(4));
        assertFalse(service.runsOn(5));
        assertFalse(service.runsOn(6));
    }

    @Test
    void weekendMaskRunsSaturdayAndSundayOnly() {
        Feed.Service service = new Feed.Service("WE", 96, 1);

        assertFalse(service.runsOn(0));
        assertFalse(service.runsOn(4));
        assertTrue(service.runsOn(5));
        assertTrue(service.runsOn(6));
    }

    @Test
    void allSevenBitsRunOnEveryDay() {
        Feed.Service service = new Feed.Service("ALL", 127, 1);

        assertTrue(service.runsOn(0));
        assertTrue(service.runsOn(1));
        assertTrue(service.runsOn(2));
        assertTrue(service.runsOn(3));
        assertTrue(service.runsOn(4));
        assertTrue(service.runsOn(5));
        assertTrue(service.runsOn(6));
    }
}
