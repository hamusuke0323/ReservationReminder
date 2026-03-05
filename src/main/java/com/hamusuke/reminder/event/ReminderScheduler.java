package com.hamusuke.reminder.event;

import com.google.common.collect.Lists;
import com.hamusuke.reminder.ReservationReminder;
import com.hamusuke.reminder.reminders.TwiceRemindTask;
import com.hamusuke.reminder.reminders.message.MentionedReminderMessage;
import com.hamusuke.reminder.throwable.InvalidDurationException;
import com.hamusuke.reminder.throwable.InvalidDurationFormatException;
import com.hamusuke.reminder.throwable.QueryFailedException;
import com.hamusuke.reminder.util.DiscordChatFormatUtil;
import com.hamusuke.reminder.util.HolidayRegistry;
import com.hamusuke.reminder.web.CampusWeb;
import com.hamusuke.reminder.web.reservation.DurationContext;
import com.hamusuke.reminder.web.reservation.RateLimitedRoomReservationRetriever;
import com.hamusuke.reminder.web.reservation.RoomReservation;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.hamusuke.reminder.web.reservation.DurationContext.FORMATTER;
import static com.hamusuke.reminder.web.reservation.DurationContext.TIME_FORMATTER;

public final class ReminderScheduler extends ListenerAdapter {
    private static final DateTimeFormatter TIME_TO_STRING = DateTimeFormatter.ofPattern("HH:mm");
    private static final long CAMPUS_WEB_ACCESS_INTERVAL = 10L;
    private static final String RESERVATION_CHECKING_PROGRESS_PREFIX = "予約できるか確認しています... ";
    private static final String RESERVATION_CHECKING_PROGRESS = RESERVATION_CHECKING_PROGRESS_PREFIX + "%.1f%% (%d / %d)";
    private static final BiFunction<Integer, Integer, String> PROGRESS_TEXT_UPDATER = (cur, size) ->
            RESERVATION_CHECKING_PROGRESS.formatted((double) cur / size * 100.0D, cur, size);
    private static final Supplier<String> INTERVAL_TEXT_FACTORY = () -> {
        final var intervalEndsAt = LocalDateTime.now().plusSeconds(CAMPUS_WEB_ACCESS_INTERVAL);
        return "インターバル... " + DiscordChatFormatUtil.toTimestampRelative(intervalEndsAt) + "に再開";
    };
    private final ReservationReminder reservationReminder;

    public ReminderScheduler(final ReservationReminder reservationReminder) {
        this.reservationReminder = reservationReminder;
    }

    private static Optional<DurationContext> validate(final String hyphenatedDuration, final MessageChannelUnion ch) {
        try {
            return Optional.of(DurationContext.from(hyphenatedDuration));
        } catch (InvalidDurationFormatException e) {
            ch.sendMessage(":ng: 不正なフォーマットです: " + hyphenatedDuration + "\n例: 2025/8/21 10:00-12:00").queue();
        } catch (InvalidDurationException e) {
            ch.sendMessage(":ng: 不正な日時の範囲です: " + hyphenatedDuration).queue();
        }

        return Optional.empty();
    }

    @Override
    public synchronized void onMessageReceived(@NotNull MessageReceivedEvent event) {
        final var channelId = this.reservationReminder.getChannelId();
        final var ch = event.getChannel();
        if (event.getAuthor().isBot() || event.getAuthor().isSystem() || !ch.getId().equals(channelId)) {
            return;
        }

        if (this.isNotLoggedIn()) {
            ch.sendMessage("ログインしていません").queue();
            return;
        }

        final var reminderTasks = this.reservationReminder.getReminderTasks();
        final var lines = event.getMessage().getContentStripped().split("\n");
        final int size = reminderTasks.size();
        final var toBeReserved = Arrays.stream(lines)
                .map(String::trim)
                .map(hyphenatedDuration -> validate(hyphenatedDuration, ch))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();

        this.startScheduling(toBeReserved, ch);
        if (reminderTasks.size() > size) {
            reminderTasks.save();
        }
    }

    private void startScheduling(final List<DurationContext> durationContexts, final MessageChannelUnion ch) {
        if (this.isNotLoggedIn()) {
            return;
        }

        final var debugProfiler = this.reservationReminder.getDebugProfiler();
        final List<String> results = Lists.newArrayList();
        final AtomicReference<Message> message = new AtomicReference<>();

        RateLimitedRoomReservationRetriever.startRetrievingBlocking(durationContexts, this.getCampusWeb(), debugProfiler, (cur, total) -> {
            if (message.get() != null) {
                message.get().editMessage(PROGRESS_TEXT_UPDATER.apply(cur, total)
                                + "\n"
                                + INTERVAL_TEXT_FACTORY.get())
                        .queue();
                return;
            }

            final var msg = ch.sendMessage(PROGRESS_TEXT_UPDATER.apply(cur, total)).complete();
            message.set(msg);
        }, (ctx, other) -> {
            final var result = this.trySchedule(ctx, other);
            debugProfiler.appendLine("Result: " + result);
            results.add(result);
        }, () -> {
            results.forEach(result -> ch.sendMessage(result).queue());
            if (message.get() != null) {
                message.get().editMessage(RESERVATION_CHECKING_PROGRESS_PREFIX + "完了").queue();
            }
        }, e -> ch.sendMessage(e).queue());
    }

    private String trySchedule(final DurationContext ctx, final @Nullable RoomReservation otherReservation) {
        final var debugProfiler = this.reservationReminder.getDebugProfiler();
        debugProfiler.start();
        debugProfiler.appendLine("Input line: " + ctx.hyphenatedDuration());
        debugProfiler.appendLine("Parsed value: " + ctx.start().format(FORMATTER) + "-" + ctx.end().format(TIME_FORMATTER));

        if (otherReservation != null) {
            return "## :u6e80: " + ctx.hyphenatedDuration() + "\nこの時間は既に埋まっています。\n理由: " + otherReservation.reason() + "\n時間: " + otherReservation.startTime().format(TIME_TO_STRING) + "-" + otherReservation.endTime().format(TIME_TO_STRING);
        }

        final var jda = this.reservationReminder.getJDA();
        final var channelId = this.reservationReminder.getChannelId();
        final var roleId = this.reservationReminder.getRoleId();
        final var reminderTasks = this.reservationReminder.getReminderTasks();

        final var firstReminderTime = HolidayRegistry.INSTANCE
                .getBusinessDayAfter(ctx.start().toLocalDate(), -2)
                .atTime(9, 0);
        final var secondReminderTime = HolidayRegistry.INSTANCE
                .getBusinessDayAfter(firstReminderTime.toLocalDate(), 1)
                .atTime(12, 0);
        final var id = UUID.randomUUID();

        final var task = TwiceRemindTask.TwiceRemindTaskBuilder
                .of(jda, channelId, ctx.hyphenatedDuration())
                .start()
                .remindAt(firstReminderTime)
                .withMessage(MentionedReminderMessage.First.forMemberWithRole(roleId))
                .then()
                .remindAt(secondReminderTime)
                .withMessage(MentionedReminderMessage.Second.forMemberWithRole(roleId))
                .finish()
                .build();

        reminderTasks.add(id, task);
        return "## :white_check_mark: " + ctx.hyphenatedDuration() + "\nこの時間は予約できます（現在時点）。\nリマインドを登録しました:\n" + task + "\n- リマインドID: `" + id + "`";
    }

    public CampusWeb getCampusWeb() {
        return this.reservationReminder.getCampusWeb().orElseThrow(() -> new QueryFailedException("CampusWeb is null. This should never happen."));
    }

    public boolean isNotLoggedIn() {
        return this.reservationReminder.getCampusWeb().isEmpty();
    }
}
