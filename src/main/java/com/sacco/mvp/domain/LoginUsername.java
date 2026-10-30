package com.sacco.mvp.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/** Usernames cannot be confused with email addresses or numeric client identifiers. */
public final class LoginUsername {
    public static final String FORMAT = "[A-Za-z][A-Za-z0-9._-]{2,31}";
    private static final Pattern VALID = Pattern.compile(FORMAT);

    private LoginUsername() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }
}
