package io.github.gulliby.stoptable.format;

import io.github.gulliby.stoptable.core.Clock;
import io.github.gulliby.stoptable.core.Failure;
import io.github.gulliby.stoptable.core.Feed;
import io.github.gulliby.stoptable.core.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FeedParser {
    private FeedParser() {
    }

    public static Feed parse(List<String> lines) {
        List<Feed.Stop> stops = new ArrayList<>();
        List<Feed.Service> services = new ArrayList<>();
        List<Feed.Trip> trips = new ArrayList<>();
        Map<String, Feed.Stop> stopsById = new HashMap<>();
        Map<String, Feed.Service> servicesById = new HashMap<>();

        for (int index = 0; index < lines.size(); index++) {
            int lineNumber = index + 1;
            String source = lines.get(index);
            int first = skipHorizontal(source, 0);
            if (first == source.length() || source.charAt(first) == '#') {
                continue;
            }

            String line = trimHorizontal(source);
            List<String> words = words(line);
            String keyword = words.get(0);
            switch (keyword) {
                case "stop" -> parseStop(line, words, lineNumber, stops, stopsById);
                case "service" -> parseService(words, lineNumber, services, servicesById);
                case "trip" -> parseTrip(line, lineNumber, trips, stopsById, servicesById);
                default -> throw format(lineNumber, "unknown keyword " + Text.show(keyword));
            }
        }

        return new Feed(List.copyOf(stops), List.copyOf(services), List.copyOf(trips));
    }

    public static int parseDays(String text) {
        if (text.isEmpty()) {
            return -1;
        }

        int days = 0;
        int start = 0;
        while (start <= text.length()) {
            int comma = text.indexOf(',', start);
            int end = comma < 0 ? text.length() : comma;
            if (end == start) {
                return -1;
            }

            String item = text.substring(start, end);
            int hyphen = item.indexOf('-');
            if (hyphen < 0) {
                int day = dayIndex(item);
                if (day < 0) {
                    return -1;
                }
                days |= 1 << day;
            } else {
                if (hyphen == 0 || hyphen == item.length() - 1
                        || item.indexOf('-', hyphen + 1) >= 0) {
                    return -1;
                }
                int first = dayIndex(item.substring(0, hyphen));
                int last = dayIndex(item.substring(hyphen + 1));
                if (first < 0 || last < first) {
                    return -1;
                }
                for (int day = first; day <= last; day++) {
                    days |= 1 << day;
                }
            }

            if (comma < 0) {
                return days;
            }
            start = comma + 1;
        }
        return -1;
    }

    private static void parseStop(
            String line,
            List<String> words,
            int lineNumber,
            List<Feed.Stop> stops,
            Map<String, Feed.Stop> stopsById) {
        if (words.size() < 3) {
            throw format(lineNumber, "expected \"stop ID NAME\"");
        }

        String id = words.get(1);
        String name = restAfterWords(line, 2);
        if (!validId(id)) {
            throw format(lineNumber, "invalid stop id " + Text.show(id));
        }
        if (!validStopName(name)) {
            throw format(lineNumber, "invalid stop name " + Text.show(name));
        }
        if (stopsById.containsKey(id)) {
            throw format(lineNumber, "duplicate stop " + Text.show(id));
        }

        Feed.Stop stop = new Feed.Stop(id, name, lineNumber);
        stops.add(stop);
        stopsById.put(id, stop);
    }

    private static void parseService(
            List<String> words,
            int lineNumber,
            List<Feed.Service> services,
            Map<String, Feed.Service> servicesById) {
        if (words.size() != 3) {
            throw format(lineNumber, "expected \"service ID DAYS\"");
        }

        String id = words.get(1);
        String dayText = words.get(2);
        if (!validId(id)) {
            throw format(lineNumber, "invalid service id " + Text.show(id));
        }
        int days = parseDays(dayText);
        if (days < 0) {
            throw format(lineNumber, "invalid days " + Text.show(dayText));
        }
        if (servicesById.containsKey(id)) {
            throw format(lineNumber, "duplicate service " + Text.show(id));
        }

        Feed.Service service = new Feed.Service(id, days, lineNumber);
        services.add(service);
        servicesById.put(id, service);
    }

    private static void parseTrip(
            String line,
            int lineNumber,
            List<Feed.Trip> trips,
            Map<String, Feed.Stop> stopsById,
            Map<String, Feed.Service> servicesById) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            throw format(lineNumber, "expected \"trip ROUTE SERVICE: STOP TIME > STOP TIME\"");
        }

        List<String> header = words(trimHorizontal(line.substring(0, colon)));
        if (header.size() != 3) {
            throw format(lineNumber, "expected \"trip ROUTE SERVICE: STOP TIME > STOP TIME\"");
        }

        String route = header.get(1);
        String serviceId = header.get(2);
        if (!validId(route)) {
            throw format(lineNumber, "invalid route " + Text.show(route));
        }
        if (!servicesById.containsKey(serviceId)) {
            throw format(lineNumber, "unknown service " + Text.show(serviceId));
        }

        String callsText = line.substring(colon + 1);
        List<Feed.Call> calls = new ArrayList<>();
        Set<String> usedStops = new HashSet<>();
        int previousMinutes = -1;
        int segmentStart = 0;
        while (segmentStart <= callsText.length()) {
            int separator = callsText.indexOf('>', segmentStart);
            int segmentEnd = separator < 0 ? callsText.length() : separator;
            List<String> callWords = words(trimHorizontal(callsText.substring(segmentStart, segmentEnd)));
            if (callWords.size() != 2) {
                throw format(lineNumber, "expected \"STOP TIME\"");
            }

            String stopId = callWords.get(0);
            String timeText = callWords.get(1);
            if (!stopsById.containsKey(stopId)) {
                throw format(lineNumber, "unknown stop " + Text.show(stopId));
            }
            int minutes = Clock.parse(timeText);
            if (minutes < 0) {
                throw format(lineNumber, "invalid time " + Text.show(timeText));
            }
            if (!usedStops.add(stopId)) {
                throw format(lineNumber, "stop " + Text.show(stopId) + " is used twice in one trip");
            }
            if (previousMinutes >= 0 && minutes <= previousMinutes) {
                throw format(lineNumber, "time must be later than the previous stop");
            }

            calls.add(new Feed.Call(stopId, minutes));
            previousMinutes = minutes;
            if (separator < 0) {
                break;
            }
            segmentStart = separator + 1;
        }

        if (calls.size() < 2) {
            throw format(lineNumber, "a trip needs at least 2 stops");
        }
        if (calls.size() > 50) {
            throw format(lineNumber, "a trip has at most 50 stops");
        }

        trips.add(new Feed.Trip(route, serviceId, List.copyOf(calls), lineNumber));
    }

    private static int dayIndex(String day) {
        for (int index = 0; index < Feed.DAYS.size(); index++) {
            if (Feed.DAYS.get(index).equals(day)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean validId(String id) {
        if (id.isEmpty() || id.length() > 8) {
            return false;
        }
        for (int index = 0; index < id.length(); index++) {
            char character = id.charAt(index);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean validStopName(String name) {
        if (name.isEmpty() || name.length() > 30) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            char character = name.charAt(index);
            if (!(character >= 'A' && character <= 'Z')
                    && !(character >= 'a' && character <= 'z')
                    && !(character >= '0' && character <= '9')
                    && character != ' '
                    && character != '.'
                    && character != ','
                    && character != '\''
                    && character != '-'
                    && character != '&'
                    && character != '/'
                    && character != '('
                    && character != ')') {
                return false;
            }
        }
        return true;
    }

    private static List<String> words(String text) {
        List<String> result = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            index = skipHorizontal(text, index);
            if (index == text.length()) {
                break;
            }
            int start = index;
            while (index < text.length() && !isHorizontal(text.charAt(index))) {
                index++;
            }
            result.add(text.substring(start, index));
        }
        return result;
    }

    private static String restAfterWords(String text, int count) {
        int index = 0;
        for (int word = 0; word < count; word++) {
            index = skipHorizontal(text, index);
            while (index < text.length() && !isHorizontal(text.charAt(index))) {
                index++;
            }
        }
        index = skipHorizontal(text, index);
        return trimHorizontal(text.substring(index));
    }

    private static String trimHorizontal(String text) {
        int start = skipHorizontal(text, 0);
        int end = text.length();
        while (end > start && isHorizontal(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static int skipHorizontal(String text, int index) {
        while (index < text.length() && isHorizontal(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isHorizontal(char character) {
        return character == ' ' || character == '\t';
    }

    private static Failure.Format format(int line, String message) {
        return new Failure.Format(line, message);
    }
}
