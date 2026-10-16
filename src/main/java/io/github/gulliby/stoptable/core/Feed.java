package io.github.gulliby.stoptable.core;

import java.util.List;

public record Feed(List<Stop> stops, List<Service> services, List<Trip> trips) {
    public static final List<String> DAYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");

    public record Stop(String id, String name, int line) {
    }

    public record Service(String id, int days, int line) {
        public boolean runsOn(int day) {
            return (days & (1 << day)) != 0;
        }
    }

    public record Call(String stop, int minutes) {
    }

    public record Trip(String route, String service, List<Call> calls, int line) {
    }

    public Stop stop(String id) {
        for (Stop stop : stops) {
            if (stop.id().equals(id)) {
                return stop;
            }
        }
        return null;
    }

    public Service service(String id) {
        for (Service service : services) {
            if (service.id().equals(id)) {
                return service;
            }
        }
        return null;
    }
}
