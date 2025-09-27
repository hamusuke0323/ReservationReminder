package com.hamusuke.reminder.event;

import com.hamusuke.reminder.ReservationReminder;
import com.hamusuke.reminder.reminders.FriendlyReminderMessage;
import com.hamusuke.reminder.reminders.ReminderTasks;
import com.hamusuke.reminder.reminders.TwiceRemindTask;
import com.hamusuke.reminder.throwable.QueryFailedException;
import com.hamusuke.reminder.util.HolidayRegistry;
import com.hamusuke.reminder.web.CampusWeb;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

public final class ReminderScheduler extends ListenerAdapter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d H:mm");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter TIME_TO_STRING = DateTimeFormatter.ofPattern("HH:mm");
    private static final String ROOM = "F1会議室";
    private final ReservationReminder reservationReminder;
    private final JDA jda;
    private final String channelId;
    private final String roleId;
    private final ReminderTasks reminderTasks;

    public ReminderScheduler(final ReservationReminder reservationReminder) {
        this.reservationReminder = reservationReminder;
        this.jda = reservationReminder.getJDA();
        this.channelId = reservationReminder.getChannelId();
        this.roleId = reservationReminder.getRoleId();
        this.reminderTasks = reservationReminder.getReminderTasks();
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) {
            return;
        }

        final var ch = event.getChannel();
        if (!ch.getId().equals(this.channelId)) {
            return;
        }

        if (this.isNotLoggedIn()) {
            ch.sendMessage("ログインしていません").queue();
            return;
        }

        final var lines = event.getMessage().getContentStripped().split("\n");
        final int size = this.reminderTasks.size();
        Arrays.stream(lines)
                .map(String::trim)
                .map(hyphenatedDuration -> this.validate(hyphenatedDuration, ch))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(ctx -> this.trySchedule(ctx, ch));

        if (this.reminderTasks.size() > size) {
            this.reminderTasks.save();
        }
    }

    private Optional<DurationContext> validate(final String hyphenatedDuration, final MessageChannelUnion ch) {
        final var dateTime = hyphenatedDuration.split("-");
        if (dateTime.length != 2) {
            ch.sendMessage(":ng: 不正なフォーマットです: " + hyphenatedDuration + "\n例: 2025/8/21 10:00-12:00").queue();
            return Optional.empty();
        }

        try {
            final var start = LocalDateTime.parse(dateTime[0], FORMATTER);
            final var end = LocalDateTime.of(start.toLocalDate(), LocalTime.parse(dateTime[1], TIME_FORMATTER));
            if (start.isAfter(end)) {
                ch.sendMessage(":ng: 不正な日時の範囲です: " + hyphenatedDuration).queue();
                return Optional.empty();
            }

            return Optional.of(new DurationContext(hyphenatedDuration, start, end));
        } catch (DateTimeParseException e) {
            ch.sendMessage(":ng: 不正なフォーマットです: " + hyphenatedDuration + "\n例: 2025/8/21 10:00-12:00").queue();
        }

        return Optional.empty();
    }

    private void trySchedule(final DurationContext ctx, final MessageChannelUnion ch) {
        try {
            final var reservations = this.getCampusWeb().queryRoomReservation(ROOM, ctx.start().toLocalDate());
            final var reservation = reservations.cannotReserve(ctx.start(), ctx.end());
            if (reservation != null) {
                ch.sendMessage("## :u6e80: " + ctx.hyphenatedDuration() + "\nこの時間は既に埋まっています。\n理由: " + reservation.reason() + "\n時間: " + reservation.startTime().format(TIME_TO_STRING) + "-" + reservation.endTime().format(TIME_TO_STRING)).queue();
                return;
            }

            final var firstReminderTime = HolidayRegistry.INSTANCE
                    .getBusinessDayAfter(ctx.start().toLocalDate(), -2)
                    .atTime(9, 0);
            final var secondReminderTime = HolidayRegistry.INSTANCE
                    .getBusinessDayAfter(firstReminderTime.toLocalDate(), 1)
                    .atTime(12, 0);
            final var id = UUID.randomUUID();

            final var task = TwiceRemindTask.TwiceRemindTaskBuilder
                    .of(this.jda, this.channelId, ctx.hyphenatedDuration())
                    .start()
                    .remindAt(firstReminderTime)
                    .withMessage(FriendlyReminderMessage.First.mentionEveryoneWhoHas(this.roleId))
                    .then()
                    .remindAt(secondReminderTime)
                    .withMessage(FriendlyReminderMessage.Second.mentionEveryoneWhoHas(this.roleId))
                    .finish()
                    .build();

            this.reminderTasks.add(id, task);
            ch.sendMessage("## :white_check_mark: " + ctx.hyphenatedDuration() + "\nこの時間は予約できます（現在時点）。\nリマインドを登録しました:\n" + task + "\n- リマインドID: `" + id + "`").queue();
        } catch (QueryFailedException e) {
            ch.sendMessage(":x: 学務システムへのアクセスに失敗しました").queue();
        }
    }

    public CampusWeb getCampusWeb() {
        return this.reservationReminder.getCampusWeb().orElseThrow();
    }

    public boolean isNotLoggedIn() {
        return this.reservationReminder.getCampusWeb().isEmpty();
    }

    private record DurationContext(String hyphenatedDuration, LocalDateTime start, LocalDateTime end) {
    }
}
