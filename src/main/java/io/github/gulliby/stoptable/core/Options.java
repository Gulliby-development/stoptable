package io.github.gulliby.stoptable.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Options {
    private Options() {
    }

    public enum Kind {
        INT,
        TIME,
        CHOICE
    }

    public record Spec(String name, Kind kind, int lo, int hi, List<String> choices, int fallback) {
        public static Spec integer(String name, int lo, int hi, int fallback) {
            return new Spec(name, Kind.INT, lo, hi, List.of(), fallback);
        }

        public static Spec time(String name, int fallback) {
            return new Spec(name, Kind.TIME, 0, 0, List.of(), fallback);
        }

        public static Spec choice(String name, List<String> choices, int fallback) {
            return new Spec(name, Kind.CHOICE, 0, 0, choices, fallback);
        }
    }

    public record Parsed(Map<String, Integer> values, List<String> args) {
    }

    public static Parsed parse(List<String> argv, List<Spec> specs) {
        Map<String, Spec> specsByName = new LinkedHashMap<>();
        for (Spec spec : specs) {
            specsByName.put(spec.name(), spec);
        }

        Map<String, String> supplied = new LinkedHashMap<>();
        List<String> args = new ArrayList<>();
        for (int index = 0; index < argv.size(); index++) {
            String token = argv.get(index);
            if (token.equals("-") || !token.startsWith("-")) {
                args.add(token);
                continue;
            }

            int equals = token.indexOf('=');
            String name = equals < 0 ? token : token.substring(0, equals);
            Spec spec = specsByName.get(name);
            if (spec == null) {
                throw new Failure.Usage("unknown option " + Text.show(token));
            }

            String value;
            if (equals >= 0) {
                value = token.substring(equals + 1);
            } else if (index + 1 < argv.size()) {
                value = argv.get(++index);
            } else {
                throw new Failure.Usage(name + " needs a value");
            }
            supplied.put(name, value);
        }

        Map<String, Integer> values = new LinkedHashMap<>();
        for (Spec spec : specs) {
            String value = supplied.get(spec.name());
            if (value == null && !supplied.containsKey(spec.name())) {
                values.put(spec.name(), spec.fallback());
                continue;
            }

            values.put(spec.name(), validate(spec, value));
        }
        return new Parsed(values, args);
    }

    private static int validate(Spec spec, String value) {
        return switch (spec.kind()) {
            case INT -> {
                if (!isInteger(value)) {
                    throw invalidInteger(spec, value);
                }
                int number = Integer.parseInt(value);
                if (number < spec.lo() || number > spec.hi()) {
                    throw invalidInteger(spec, value);
                }
                yield number;
            }
            case TIME -> {
                int minutes = Clock.parse(value);
                if (minutes < 0) {
                    throw new Failure.Usage("invalid " + spec.name() + " " + Text.show(value)
                            + " (expected a time from 00:00 to 23:59)");
                }
                yield minutes;
            }
            case CHOICE -> {
                int choice = spec.choices().indexOf(value);
                if (choice < 0) {
                    throw new Failure.Usage("invalid " + spec.name() + " " + Text.show(value)
                            + " (expected " + Text.listOr(spec.choices()) + ")");
                }
                yield choice;
            }
        };
    }

    private static Failure.Usage invalidInteger(Spec spec, String value) {
        return new Failure.Usage("invalid " + spec.name() + " " + Text.show(value)
                + " (expected an integer from " + spec.lo() + " to " + spec.hi() + ")");
    }

    private static boolean isInteger(String value) {
        if (value.isEmpty() || value.length() > 9) {
            return false;
        }
        if (value.equals("0")) {
            return true;
        }
        if (value.charAt(0) < '1' || value.charAt(0) > '9') {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            if (value.charAt(index) < '0' || value.charAt(index) > '9') {
                return false;
            }
        }
        return true;
    }
}
