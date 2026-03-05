package com.hamusuke.reminder.web.reservation;

import com.hamusuke.reminder.throwable.InvalidDurationException;
import com.hamusuke.reminder.throwable.InvalidDurationFormatException;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public record DurationContext(String hyphenatedDuration, LocalDateTime start, LocalDateTime end) {
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d H:mm");
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    public static DurationContext from(final String hyphenatedDuration) {
        final var dateTime = hyphenatedDuration.split("-");
        if (dateTime.length != 2) {
            throw new InvalidDurationFormatException();
        }

        try {
            final var start = LocalDateTime.parse(dateTime[0], FORMATTER);
            final var end = LocalDateTime.of(start.toLocalDate(), LocalTime.parse(dateTime[1], TIME_FORMATTER));
            if (start.isAfter(end)) {
                throw new InvalidDurationException();
            }

            return new DurationContext(hyphenatedDuration, start, end);
        } catch (DateTimeParseException ignored) {
            throw new InvalidDurationFormatException();
        }
    }
}
