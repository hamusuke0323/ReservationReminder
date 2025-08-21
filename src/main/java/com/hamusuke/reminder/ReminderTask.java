package com.hamusuke.reminder;

import com.hamusuke.reminder.util.Util;
import net.dv8tion.jda.api.JDA;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ReminderTask {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "1st Reminder Thread"));
    private final ScheduledExecutorService executor2 = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "2nd Reminder Thread"));
    private boolean allRemindersDone = false;
    private final JDA jda;
    private final String channelId;
    private final String message;
    private final String message2;

    public ReminderTask(final JDA jda, final String channelId, final String message, final String message2, final LocalDateTime remindTime, final LocalDateTime remindTime2) {
        this.jda = jda;
        this.channelId = channelId;
        this.message = message;
        this.message2 = message2;

        final var now = LocalDateTime.now();
        final var secs = Duration.between(now, remindTime).getSeconds();
        final var secs2 = Duration.between(now, remindTime2).getSeconds();

        if (secs > 0) {
            this.executor.schedule(this::send1stReminder, secs, TimeUnit.SECONDS);
        } else {
            Util.shutdownExecutor(this.executor);
        }

        if (secs2 > 0) {
            this.executor2.schedule(this::send2ndReminder, Duration.between(LocalDateTime.now(), remindTime2).getSeconds(), TimeUnit.SECONDS);
        } else {
            Util.shutdownExecutor(this.executor2);
            this.allRemindersDone = true;
        }
    }

    private void send1stReminder() {
        Util.shutdownExecutor(this.executor);
        final var ch = this.jda.getTextChannelById(this.channelId);
        if (ch == null) {
            System.err.println("Failed to get text channel. This should never happen!");
            return;
        }

        ch.sendMessage(this.message).queue();
    }

    private void send2ndReminder() {
        Util.shutdownExecutor(this.executor2);
        this.allRemindersDone = true;

        final var ch = this.jda.getTextChannelById(this.channelId);
        if (ch == null) {
            System.err.println("Failed to get text channel. This should never happen!");
            return;
        }

        ch.sendMessage(this.message2).queue();
    }

    public void kill() {
        Util.shutdownExecutor(this.executor);
        Util.shutdownExecutor(this.executor2);
        this.allRemindersDone = true;
    }

    public boolean areAllRemindersDone() {
        return this.allRemindersDone;
    }

    public String getMessage() {
        return this.message;
    }

    public String getMessage2() {
        return this.message2;
    }
}
