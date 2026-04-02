package com.hamusuke.reminder.command.commands;

import com.google.common.collect.Lists;
import com.hamusuke.reminder.command.Command;
import com.hamusuke.reminder.command.CommandContext;
import com.hamusuke.reminder.profiler.DebugProfiler;
import com.hamusuke.reminder.reminders.TwiceRemindTask;
import com.hamusuke.reminder.util.DiscordChatFormatUtil;
import com.hamusuke.reminder.web.CampusWeb;
import com.hamusuke.reminder.web.reservation.DurationContext;
import com.hamusuke.reminder.web.reservation.RateLimitedRoomReservationRetriever;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.hamusuke.reminder.web.reservation.RateLimitedRoomReservationRetriever.CAMPUS_WEB_ACCESS_INTERVAL;

public final class RecheckCommand implements Command {
    public static final RecheckCommand INSTANCE = new RecheckCommand();
    private static final DateTimeFormatter TIME_TO_STRING = DateTimeFormatter.ofPattern("HH:mm");
    private static final String RESERVATION_CHECKING_PROGRESS_PREFIX = "再確認しています... ";
    private static final String RESERVATION_CHECKING_PROGRESS = RESERVATION_CHECKING_PROGRESS_PREFIX + "%.1f%% (%d / %d)";
    private static final BiFunction<Integer, Integer, String> PROGRESS_TEXT_UPDATER = (cur, size) ->
            RESERVATION_CHECKING_PROGRESS.formatted((double) cur / size * 100.0D, cur, size);
    private static final Supplier<String> INTERVAL_TEXT_FACTORY = () -> {
        final var intervalEndsAt = LocalDateTime.now().plusSeconds(CAMPUS_WEB_ACCESS_INTERVAL);
        return "インターバル... " + DiscordChatFormatUtil.toTimestampRelative(intervalEndsAt) + "に再開";
    };

    private RecheckCommand() {
    }

    @Override
    public String getName() {
        return "recheck";
    }

    private static void startRechecking(final CampusWeb campusWeb, final List<DurationContext> durationContexts, final InteractionHook hook) {
        final AtomicReference<Message> message = new AtomicReference<>();
        final List<String> results = Lists.newArrayList();

        RateLimitedRoomReservationRetriever.startRetrievingBlocking(durationContexts, campusWeb, DebugProfiler.EMPTY, (cur, total) -> {
            if (message.get() != null) {
                message.get().editMessage(PROGRESS_TEXT_UPDATER.apply(cur, total)
                                + "\n"
                                + INTERVAL_TEXT_FACTORY.get())
                        .queue();
                return;
            }

            final var msg = hook.sendMessage(PROGRESS_TEXT_UPDATER.apply(cur, total)).complete();
            message.set(msg);
        }, (ctx, other) -> {
            if (other != null) {
                results.add("## :u6e80: " + ctx.hyphenatedDuration() + "\nこの時間は既に埋まっています。\n理由: " + other.reason() + "\n時間: " + other.startTime().format(TIME_TO_STRING) + "-" + other.endTime().format(TIME_TO_STRING));
                return;
            }

            results.add("## :white_check_mark: " + ctx.hyphenatedDuration() + "\nこの時間はまだ予約できます（現在時点）。");
        }, () -> {
            results.forEach(result -> hook.sendMessage(result).queue());
            if (message.get() != null) {
                message.get().editMessage(RESERVATION_CHECKING_PROGRESS_PREFIX + "完了").queue();
            }
        }, e -> hook.sendMessage(e).queue());
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash(this.getName(), "施設が利用可能かどうか再確認します");
    }

    @Override
    public void execute(CommandContext context) {
        context.event().deferReply(true).queue();
        final var hook = context.event().getHook();

        if (context.campusWeb().isEmpty()) {
            hook.setEphemeral(true).sendMessage("ログインしていません").queue();
            return;
        }

        final var durationContexts = context.reservationReminder().getReminderTasks()
                .entrySet().stream()
                .map(Map.Entry::getValue)
                .filter(t -> !t.areAllRemindersDone())
                .map(TwiceRemindTask::getHyphenatedDuration)
                .map(DurationContext::from)
                .toList();

        if (durationContexts.isEmpty()) {
            hook.setEphemeral(true).sendMessage("まだリマインドがありません").queue();
            return;
        }

        startRechecking(context.campusWeb().get(), durationContexts, hook);
    }
}
