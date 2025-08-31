package com.hamusuke.reminder;

import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hamusuke.reminder.util.Util;
import net.dv8tion.jda.api.JDA;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ReminderTask {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d H:mm");
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "1st Reminder Thread"));
    private final ScheduledExecutorService executor2 = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "2nd Reminder Thread"));
    private boolean allRemindersDone = false;
    private final JDA jda;
    private final String channelId;
    private final String reservationDuration;
    private final String message;
    private final String message2;
    private final LocalDateTime remindTime;
    private final LocalDateTime remindTime2;

    public ReminderTask(final JDA jda, final String channelId, final String reservationDuration, final String message, final String message2, final LocalDateTime remindTime, final LocalDateTime remindTime2) {
        this.jda = jda;
        this.channelId = channelId;
        this.reservationDuration = reservationDuration;
        this.message = message;
        this.message2 = message2;
        this.remindTime = remindTime;
        this.remindTime2 = remindTime2;

        final var now = LocalDateTime.now();
        final var secs = Duration.between(now, remindTime).getSeconds();
        final var secs2 = Duration.between(now, remindTime2).getSeconds();

        if (secs > 0) {
            this.executor.schedule(this::send1stReminder, secs, TimeUnit.SECONDS);
        } else {
            this.executor.shutdownNow();
        }

        if (secs2 > 0) {
            this.executor2.schedule(this::send2ndReminder, secs2, TimeUnit.SECONDS);
        } else {
            this.executor2.shutdownNow();
            this.allRemindersDone = true;
        }
    }

    public static ReminderTask from(final JsonObject obj, final JDA jda, final String channelId, final String message, final String message2) {
        final var duration = obj.get("duration").getAsString();
        final var first = LocalDateTime.parse(obj.get("first").getAsString(), FORMATTER);
        final var second = LocalDateTime.parse(obj.get("second").getAsString(), FORMATTER);

        return new ReminderTask(jda, channelId, duration, message, message2, first, second);
    }

    private void send1stReminder() {
        this.executor.shutdownNow();
        final var ch = this.jda.getTextChannelById(this.channelId);
        if (ch == null) {
            System.err.println("Failed to get text channel. This should never happen!");
            return;
        }

        ch.sendMessage(this.message).queue();
    }

    private void send2ndReminder() {
        this.executor2.shutdownNow();
        this.allRemindersDone = true;

        final var ch = this.jda.getTextChannelById(this.channelId);
        if (ch == null) {
            System.err.println("Failed to get text channel. This should never happen!");
            return;
        }

        ch.sendMessage(this.message2).queue();
    }

    public void kill() {
        this.executor.shutdownNow();
        this.executor2.shutdownNow();
        this.allRemindersDone = true;
    }

    public boolean areAllRemindersDone() {
        return this.allRemindersDone;
    }

    public String getReservationDuration() {
        return this.reservationDuration;
    }

    public String getMessage() {
        return this.message;
    }

    public String getMessage2() {
        return this.message2;
    }

    public void writeTo(final JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("duration").value(this.reservationDuration);
        writer.name("first").value(this.remindTime.format(FORMATTER));
        writer.name("second").value(this.remindTime2.format(FORMATTER));
        writer.endObject();
    }

    @Override
    public String toString() {
        return "- 最初のリマインド: " +
                this.remindTime.format(FORMATTER) +
                " (" + Util.toTimestampRelative(this.remindTime) + ")" +
                "\n- 2番目のリマインド: " +
                this.remindTime2.format(FORMATTER) +
                " (" + Util.toTimestampRelative(this.remindTime2) + ")";
    }
}
