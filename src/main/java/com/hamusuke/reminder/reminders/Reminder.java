package com.hamusuke.reminder.reminders;

import com.hamusuke.reminder.util.DiscordChatFormatUtil;
import net.dv8tion.jda.api.JDA;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Reminder {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d H:mm");
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Reminder Thread"));
    private final JDA jda;
    private final String channelId;
    private final FriendlyReminderMessage message;
    private final LocalDateTime remindTime;
    private final AtomicBoolean done = new AtomicBoolean();

    public Reminder(final JDA jda, final String channelId, final FriendlyReminderMessage message, final LocalDateTime remindTime) {
        this.jda = jda;
        this.channelId = channelId;
        this.message = message;
        this.remindTime = remindTime;

        final var now = LocalDateTime.now();
        final var delay = Duration.between(now, remindTime).getSeconds();
        if (delay <= 0) {
            this.executor.shutdownNow();
            this.done.set(true);
            return;
        }

        this.executor.schedule(this::send, delay, TimeUnit.SECONDS);
    }

    public static Reminder parse(final String dateTime, final JDA jda, final String channelId, final FriendlyReminderMessage message) {
        return new Reminder(jda, channelId, message, LocalDateTime.parse(dateTime, FORMATTER));
    }

    public Reminder withNewRemindTime(final LocalDateTime remindTime) {
        return new Reminder(this.jda, this.channelId, this.message, remindTime);
    }

    public void send() {
        this.executor.shutdownNow();
        this.done.set(true);

        final var ch = this.jda.getTextChannelById(this.channelId);
        if (ch == null) {
            System.err.println("Failed to get text channel. This should never happen!");
            return;
        }

        ch.sendMessage(this.message.get()).queue();
    }

    public void kill() {
        this.executor.shutdownNow();
        this.done.set(true);
    }

    public boolean isDone() {
        return this.done.get();
    }

    public String formatRemindTime() {
        return this.remindTime.format(FORMATTER);
    }

    @Override
    public String toString() {
        return this.formatRemindTime() + " (" + DiscordChatFormatUtil.toTimestampRelative(this.remindTime) + ")";
    }
}
