package com.hamusuke.reminder;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class BusinessDay {
    private static final Gson GSON = new Gson();
    private static final String HOLIDAYS_API = "https://holidays-jp.github.io/api/v1/%d/date.json";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final Map<Integer, Set<LocalDate>> holidayMap = Maps.newConcurrentMap();

    public LocalDate getBusinessDayAfter(final LocalDate base, final int days) {
        if (days == 0) {
            return base;
        }

        var date = base;
        for (int i = 0; i < Math.abs(days); i++) {
            do {
                date = date.plusDays(days > 0 ? 1 : -1);
            } while (this.isHoliday(date));
        }

        return date;
    }

    public boolean isHoliday(final LocalDate date) {
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return true;
        }

        final var now = LocalDate.now();
        if (now.getYear() > date.getYear()) {
            return false;
        }

        if (this.holidayMap.containsKey(date.getYear())) {
            return this.holidayMap.get(date.getYear()).contains(date);
        }

        try (final var isr = new InputStreamReader(new URL(HOLIDAYS_API.formatted(date.getYear())).openStream(), StandardCharsets.UTF_8)) {
            final var obj = GSON.fromJson(isr, JsonObject.class);
            this.holidayMap.put(date.getYear(), obj.keySet().stream().map(k -> LocalDate.parse(k, FORMATTER)).collect(Collectors.toUnmodifiableSet()));
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (this.holidayMap.containsKey(date.getYear())) {
            return this.holidayMap.get(date.getYear()).contains(date);
        }

        return false;
    }
}
