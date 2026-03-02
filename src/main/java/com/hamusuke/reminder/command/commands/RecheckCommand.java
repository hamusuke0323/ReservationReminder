package com.hamusuke.reminder.command.commands;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.RateLimiter;
import com.hamusuke.reminder.command.Command;
import com.hamusuke.reminder.command.CommandContext;
import com.hamusuke.reminder.profiler.DebugProfiler;
import com.hamusuke.reminder.reminders.TwiceRemindTask;
import com.hamusuke.reminder.throwable.QueryFailedException;
import com.hamusuke.reminder.util.DiscordChatFormatUtil;
import com.hamusuke.reminder.web.CampusWeb;
import com.hamusuke.reminder.web.reservation.DurationContext;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.internal.utils.tuple.Pair;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class RecheckCommand implements Command {
    public static final RecheckCommand INSTANCE = new RecheckCommand();
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final DateTimeFormatter TIME_TO_STRING = DateTimeFormatter.ofPattern("HH:mm");
    private static final String ROOM = "F1会議室";
    private static final long CAMPUS_WEB_ACCESS_INTERVAL = 10L;
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

    private static void startRetrieving(final CampusWeb campusWeb, final List<DurationContext> durationContexts, final InteractionHook hook) {
        final int size = durationContexts.size();
        hook.sendMessage(PROGRESS_TEXT_UPDATER.apply(0, size))
                .map(it -> {
                    final var limiter = RateLimiter.create(1.0D / CAMPUS_WEB_ACCESS_INTERVAL);
                    limiter.acquire();

                    final List<String> results = Lists.newArrayList();
                    for (int i = 0; i < size; i++) {
                        try {
                            final var result = recheck(campusWeb, durationContexts.get(i));
                            results.add(result);

                            it.editMessage(PROGRESS_TEXT_UPDATER.apply(i + 1, size)
                                            + "\n"
                                            + INTERVAL_TEXT_FACTORY.get())
                                    .queue();
                            limiter.acquire();
                        } catch (QueryFailedException e) {
                            System.err.println("Error occurred while trying to schedule: " + e.getMessage());
                            throw e;
                        }
                    }

                    return Pair.of(results, it);
                })
                .queue(pair -> {
                    pair.getLeft().forEach(result -> hook.sendMessage(result).queue());
                    pair.getRight().editMessage(RESERVATION_CHECKING_PROGRESS_PREFIX + "完了").queue();
                }, t -> hook.sendMessage("処理中にエラーが発生しました。").queue());
    }

    private static String recheck(final CampusWeb campusWeb, final DurationContext ctx) {
        final var reservations = campusWeb.queryRoomReservation(ROOM, ctx.start().toLocalDate(), DebugProfiler.EMPTY);
        final var reservation = reservations.cannotReserve(ctx.start(), ctx.end());
        if (reservation != null) {
            return "## :u6e80: " + ctx.hyphenatedDuration() + "\nこの時間は既に埋まっています。\n理由: " + reservation.reason() + "\n時間: " + reservation.startTime().format(TIME_TO_STRING) + "-" + reservation.endTime().format(TIME_TO_STRING);
        }

        return "## :white_check_mark: " + ctx.hyphenatedDuration() + "\nこの時間はまだ予約できます（現在時点）。";
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

        if (!LOCK.tryLock()) {
            hook.setEphemeral(true).sendMessage("処理中です").queue();
            return;
        }

        try {
            startRetrieving(context.campusWeb().get(), durationContexts, hook);
        } finally {
            LOCK.unlock();
        }
    }
}
