package com.hamusuke.reminder.web.reservation;

import java.time.LocalTime;

public record RoomReservation(String reason, LocalTime startTime, LocalTime endTime) {
    public boolean contains(final LocalTime time) {
        return time.equals(this.startTime) || (time.isAfter(this.startTime) && time.isBefore(this.endTime));
    }

    public boolean cannotReserve(final LocalTime start, final LocalTime end) {
        return this.contains(start) || (end.isAfter(this.startTime) && end.isBefore(this.endTime));
    }
}
