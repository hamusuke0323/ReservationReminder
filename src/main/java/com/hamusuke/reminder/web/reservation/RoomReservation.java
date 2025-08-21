package com.hamusuke.reminder.web.reservation;

import java.time.LocalDateTime;

public record RoomReservation(String reason, LocalDateTime startTime, LocalDateTime endTime) {
    public boolean contains(final LocalDateTime time) {
        return time.equals(this.startTime) || (time.isAfter(this.startTime) && time.isBefore(this.endTime));
    }

    public boolean cannotReserve(final LocalDateTime start, final LocalDateTime end) {
        return this.contains(start) || (end.isAfter(this.startTime) && end.isBefore(this.endTime));
    }
}
