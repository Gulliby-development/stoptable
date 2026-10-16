package io.github.gulliby.stoptable.core;

import java.util.List;

public final class Text {
    private Text() {
    }

    public static String show(String text) {
        StringBuilder shown = new StringBuilder(text.length() + 2);
        shown.append('"');
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (codePoint >= '!' && codePoint <= '~') {
                shown.append((char) codePoint);
            } else {
                shown.append('?');
            }
            offset += Character.charCount(codePoint);
        }
        return shown.append('"').toString();
    }

    public static String listOr(List<String> items) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < items.size(); index++) {
            if (index > 0) {
                if (index == items.size() - 1) {
                    result.append(" or ");
                } else {
                    result.append(", ");
                }
            }
            result.append(items.get(index));
        }
        return result.toString();
    }

    public static String plural(int n, String one, String many) {
        return n == 1 ? one : many;
    }

    public static List<String> renderTable(List<String> header, List<List<String>> rows) {
        int columns = header.size();
        for (List<String> row : rows) {
            columns = Math.max(columns, row.size());
        }

        int[] widths = new int[columns];
        for (int column = 0; column < header.size(); column++) {
            widths[column] = header.get(column).length();
        }
        for (List<String> row : rows) {
            for (int column = 0; column < row.size(); column++) {
                widths[column] = Math.max(widths[column], row.get(column).length());
            }
        }

        java.util.ArrayList<String> rendered = new java.util.ArrayList<>(rows.size() + 1);
        rendered.add(renderRow(header, widths));
        for (List<String> row : rows) {
            rendered.add(renderRow(row, widths));
        }
        return List.copyOf(rendered);
    }

    private static String renderRow(List<String> cells, int[] widths) {
        StringBuilder line = new StringBuilder();
        for (int column = 0; column < widths.length; column++) {
            if (column > 0) {
                line.append("  ");
            }
            String cell = column < cells.size() ? cells.get(column) : "";
            line.append(cell);
            if (column < widths.length - 1) {
                line.append(" ".repeat(widths[column] - cell.length()));
            }
        }
        while (!line.isEmpty() && line.charAt(line.length() - 1) == ' ') {
            line.setLength(line.length() - 1);
        }
        return line.toString();
    }
}
