package com.hamusuke.reminder;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.hamusuke.reminder.throwable.LoginFailedException;
import com.hamusuke.reminder.throwable.QueryFailedException;
import com.hamusuke.reminder.web.CampusWeb;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.internal.interactions.component.TextInputImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class ReservationReminder extends ListenerAdapter {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d H:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("H:mm");
    private static final DateTimeFormatter TIME_TO_STRING = DateTimeFormatter.ofPattern("HH:mm");
    private static final String ROOM = "F1会議室";
    private static final ScheduledExecutorService TASK_REMOVER = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Task Remover Thread"));
    private final JDA jda;
    private final Map<String, Consumer<SlashCommandInteractionEvent>> commands;
    @Nullable
    private CampusWeb campusWeb;
    private final String channelId;
    private final String roleId;
    private final BusinessDay businessDay = new BusinessDay();
    private final Map<UUID, ReminderTask> tasks = Maps.newConcurrentMap();

    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("<token> <channel_id> <role_id>");
            return;
        }

        new ReservationReminder(args[0], args[1], args[2]);
    }

    private ReservationReminder(final String token, final String channelId, final String roleId) {
        this.jda = JDABuilder.createLight(token, EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                .addEventListeners(this)
                .build();
        this.channelId = channelId;
        this.roleId = roleId;

        this.jda.updateCommands().addCommands(
                Commands.slash("login", "学務システムにログイン"),
                Commands.slash("logout", "ログアウト"),
                Commands.slash("chtime", "リマインドする日時を変更します")
                        .addOption(OptionType.STRING, "id", "リマインドID", true)
                        .addOption(OptionType.STRING, "first", "最初のリマインド日 (年/月/日)", true)
                        .addOption(OptionType.STRING, "second", "2番目のリマインド日（省略すると最初のリマインド日+1営業日になります）")
        ).queue();

        final Map<String, Consumer<SlashCommandInteractionEvent>> map = Maps.newHashMap();
        map.put("login", this::login);
        map.put("logout", this::logout);
        map.put("chtime", this::chtime);
        this.commands = ImmutableMap.copyOf(map);

        TASK_REMOVER.scheduleAtFixedRate(this::removeDiedTasks, 0L, 1L, TimeUnit.DAYS);
    }

    private void removeDiedTasks() {
        this.tasks.values().removeIf(ReminderTask::areAllRemindersDone);
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

        if (!this.isLoggedIn()) {
            ch.sendMessage("ログインしていません").queue();
            return;
        }

        final var lines = event.getMessage().getContentStripped().split("\n");
        for (final var line : lines) {
            this.scheduleReminder(line, ch);
        }
    }

    private void scheduleReminder(final String line, final MessageChannelUnion ch) {
        if (!this.isLoggedIn()) {
            return;
        }

        final var dateTime = line.trim().split("-");
        if (dateTime.length != 2) {
            ch.sendMessage("不正なフォーマットです: " + line + "\n例: 2025/8/21 10:00-12:00").queue();
            return;
        }

        try {
            final var date = LocalDateTime.parse(dateTime[0], FORMATTER);
            final var time = LocalDateTime.of(date.toLocalDate(), LocalTime.parse(dateTime[1], TIME_FORMATTER));
            if (date.isAfter(time)) {
                ch.sendMessage("不正な日時の範囲です: " + line).queue();
                return;
            }

            final var reservations = this.campusWeb.queryRoomReservation(ROOM, date.toLocalDate());
            final var reservation = reservations.cannotReserve(date, time);
            if (reservation != null) {
                ch.sendMessage("- " + line + "\nこの時間は既に埋まっています。\n理由: " + reservation.reason() + "\n時間: " + reservation.startTime().format(TIME_TO_STRING) + "-" + reservation.endTime().format(TIME_TO_STRING)).queue();
                return;
            }

            final var first = this.businessDay.getBusinessDayAfter(date.toLocalDate(), -2).atTime(9, 0);
            final var second = this.businessDay.getBusinessDayAfter(first.toLocalDate(), 1).atTime(12, 0);
            final var id = UUID.randomUUID();

            this.tasks.put(id, new ReminderTask(this.jda, this.channelId, "<@&" + this.roleId + "> 予約書を書きに行ってください。", "<@&" + this.roleId + "> 予約書を受け取り警備室に提出しに行ってください。", first, second));
            ch.sendMessage("## " + line + "\nこの時間は予約できます（現在時点）。\nリマインドを登録しました:\n- 最初のリマインド日: " + first.format(FORMATTER) + "\n- 2番目のリマインド日: " + second.format(FORMATTER) + "\n- リマインドID: `" + id + "`").queue();
        } catch (DateTimeParseException e) {
            ch.sendMessage("不正なフォーマットです: " + line + "\n例: 2025/8/21 10:00-12:00").queue();
        } catch (QueryFailedException e) {
            ch.sendMessage("学務システムへのアクセスに失敗しました").queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!this.commands.containsKey(event.getName())) {
            event.reply("不明なコマンドです").setEphemeral(true).queue();
            return;
        }

        this.commands.get(event.getName()).accept(event);
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        var modalId = event.getModalId().split(":");
        var userId = modalId[0];
        var op = modalId[1];
        if (!userId.equals(event.getUser().getId())) {
            event.reply("Error occurred!").setEphemeral(true).queue();
            return;
        }

        try {
            if (op.equals("login")) {
                this.handleLogin(event);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void chtime(SlashCommandInteractionEvent event) {
        final var idstr = event.getOption("id", OptionMapping::getAsString);
        final var first = event.getOption("first", OptionMapping::getAsString);
        final var second = event.getOption("second", OptionMapping::getAsString);

        if (idstr == null || first == null) {
            event.reply("ID or first was not specified").setEphemeral(true).queue();
            return;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(idstr);
        } catch (IllegalArgumentException e) {
            event.reply("不正なIDです。").setEphemeral(true).queue();
            return;
        }

        if (!this.tasks.containsKey(uuid)) {
            event.reply("見つかりませんでした。IDを確認してください。").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        final var hook = event.getHook();

        LocalDateTime firstTime;
        LocalDateTime secondTime = null;
        try {
            firstTime = LocalDate.parse(first, DATE_FORMATTER).atTime(9, 0);
            if (second != null) {
                secondTime = LocalDate.parse(second, DATE_FORMATTER).atTime(12, 0);
            } else {
                secondTime = this.businessDay.getBusinessDayAfter(firstTime.toLocalDate(), 1).atTime(12, 0);
            }
        } catch (DateTimeParseException e) {
            hook.setEphemeral(true).sendMessage("不正なフォーマットです。\n例: 2025/8/21").queue();
            return;
        }

        final var old = this.tasks.remove(uuid);
        old.kill();
        this.tasks.put(uuid, new ReminderTask(this.jda, this.channelId, old.getMessage(), old.getMessage2(), firstTime, secondTime));

        hook.sendMessage("リマインドID: `" + uuid + "` の日付を以下のように変更しました:\n- 最初のリマインド日: " + firstTime.format(FORMATTER) + "\n- 2番目のリマインド: " + secondTime.format(FORMATTER)).queue();
    }

    private void login(SlashCommandInteractionEvent event) {
        var userId = event.getUser().getId();
        event.replyModal(Modal.create(userId + ":login", "ログイン")
                .addActionRow(new TextInputImpl("sid", TextInputStyle.SHORT, "ユーザー名", -1, -1, true, null, "ユーザー名"))
                .addActionRow(new TextInputImpl("pass", TextInputStyle.SHORT, "パスワード", -1, -1, true, null, "パスワード"))
                .build()).queue();
    }

    private synchronized void handleLogin(ModalInteractionEvent event) {
        event.deferReply(true).queue();
        var hook = event.getHook();
        hook.setEphemeral(true);

        var sid = "";
        var pass = "";

        for (var m : event.getValues()) {
            switch (m.getId()) {
                case "sid":
                    sid = m.getAsString();
                    break;
                case "pass":
                    pass = m.getAsString();
                    break;
            }
        }

        var web = new CampusWeb();

        try {
            final var name = web.login(sid, pass);
            hook.sendMessage("ログイン成功！" + "ようこそ " + name + " さん").queue();
            if (this.campusWeb != null) {
                this.campusWeb.close();
            }

            this.campusWeb = web;
        } catch (LoginFailedException e) {
            hook.sendMessage("ログイン失敗！").queue();
            web.close();
        }
    }

    private synchronized void logout(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        var hook = event.getHook();
        hook.setEphemeral(true);
        if (!this.isLoggedIn()) {
            hook.sendMessage("ログインしていません").queue();
            return;
        }

        try {
            this.campusWeb.logout();
            hook.sendMessage("ログアウトしました").queue();
        } catch (Exception e) {
            hook.sendMessage("内部エラー").queue();
        } finally {
            this.campusWeb.close();
            this.campusWeb = null;
        }
    }

    private boolean isLoggedIn() {
        return this.campusWeb != null;
    }
}
