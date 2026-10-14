package com.student.smartcheck.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class DateTimeDisplay {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private DateTimeDisplay() {
    }

    public static String format(Instant instant) {
        return FORMATTER.format(instant);
    }
}
