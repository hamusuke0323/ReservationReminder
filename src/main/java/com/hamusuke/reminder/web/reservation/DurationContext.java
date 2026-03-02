package com.hamusuke.reminder.web.reservation;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public record DurationContext(String hyphenatedDuration, LocalDateTime start, LocalDateTime end) {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d H:mm");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");

    public static DurationContext from(final String hyphenatedDuration) {
        final var dateTime = hyphenatedDuration.split("-");
        if (dateTime.length != 2) {
            throw new InvalidFormatException(hyphenatedDuration);
        }

        try {
            final var start = LocalDateTime.parse(dateTime[0], FORMATTER);
            final var end = LocalDateTime.of(start.toLocalDate(), LocalTime.parse(dateTime[1], TIME_FORMATTER));
            if (start.isAfter(end)) {
                throw new InvalidDurationException(hyphenatedDuration);
            }

            return new DurationContext(hyphenatedDuration, start, end);
        } catch (DateTimeParseException ignored) {
            throw new InvalidFormatException(hyphenatedDuration);
        }
    }

    public static final class InvalidFormatException extends RuntimeException {
        public InvalidFormatException(final String message) {
            super(message);
        }
    }

    public static final class InvalidDurationException extends RuntimeException {
        public InvalidDurationException(final String message) {
            super(message);
        }
    }
}
