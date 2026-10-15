package com.sacco.mvp.accounting.dto;

import java.util.Map;
import java.util.Set;

/** Column ordering is whitelisted before scoped queries apply their limit and offset. */
public final class RegisterSort {
    private RegisterSort() {}

    public static boolean valid(String value, Set<String> columns) {
        if (value == null) return false;
        if (Set.of("newest", "oldest", "amount").contains(value)) return true;
        int separator = value.lastIndexOf('_');
        return separator > 0 && columns.contains(value.substring(0, separator))
            && Set.of("asc", "desc").contains(value.substring(separator + 1));
    }

    public static String order(String value, Map<String, String> columns, String id) {
        if (!valid(value, columns.keySet())) throw new IllegalArgumentException("filter");
        String field;
        String direction;
        switch (value) {
            case "newest" -> { field = "date"; direction = "desc"; }
            case "oldest" -> { field = "date"; direction = "asc"; }
            case "amount" -> { field = "amount"; direction = "desc"; }
            default -> {
                int separator = value.lastIndexOf('_');
                field = value.substring(0, separator);
                direction = value.substring(separator + 1);
            }
        }
        String column = columns.get(field);
        if (column == null) throw new IllegalArgumentException("filter");
        return column + " " + direction + "," + id + " " + direction;
    }
}
