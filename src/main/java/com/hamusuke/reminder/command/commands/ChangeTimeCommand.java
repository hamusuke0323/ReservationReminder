package com.hamusuke.reminder.command.commands;

import com.hamusuke.reminder.ReservationReminder;
import com.hamusuke.reminder.command.Command;
import com.hamusuke.reminder.command.CommandContext;
import com.hamusuke.reminder.util.HolidayRegistry;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collection;

public final class ChangeTimeCommand implements Command {
    public static final ChangeTimeCommand INSTANCE = new ChangeTimeCommand();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/M/d");

    private ChangeTimeCommand() {
    }

    @Override
    public String getName() {
        return "chtime";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash(this.getName(), "リマインドする日時を変更します")
                .addOption(OptionType.STRING, "id", "リマインドID", true, true)
                .addOption(OptionType.STRING, "first", "最初のリマインド日 (年/月/日)", true)
                .addOption(OptionType.STRING, "second", "2番目のリマインド日（省略すると最初のリマインド日+1営業日になります）");
    }

    @Override
    public void execute(final CommandContext context) {
        final var event = context.event();
        final var id = event.getOption("id", OptionMapping::getAsString);
        final var first = event.getOption("first", OptionMapping::getAsString);
        final var second = event.getOption("second", OptionMapping::getAsString);

        if (id == null || first == null) {
            event.reply("ID or first was not specified").setEphemeral(true).queue();
            return;
        }

        final var tasks = context.reservationReminder().getReminderTasks();
        final var uuid = tasks.validateAndFind(event, id);
        if (uuid == null) {
            return;
        }

        event.deferReply().queue();
        final var hook = event.getHook();

        LocalDateTime firstTime;
        LocalDateTime secondTime;
        try {
            firstTime = LocalDate.parse(first, DATE_FORMATTER).atTime(9, 0);
            if (second != null) {
                secondTime = LocalDate.parse(second, DATE_FORMATTER).atTime(12, 0);
            } else {
                secondTime = HolidayRegistry.INSTANCE.getBusinessDayAfter(firstTime.toLocalDate(), 1).atTime(12, 0);
            }
        } catch (DateTimeParseException e) {
            hook.setEphemeral(true).sendMessage("不正なフォーマットです。\n例: 2025/8/21").queue();
            return;
        }

        final var old = tasks.remove(uuid);
        old.kill();
        final var newTask = old.withNewRemindTimes(firstTime, secondTime);
        tasks.add(uuid, newTask);

        hook.sendMessage(old.getHyphenatedDuration() + "の予約のためのリマインド日時を以下のように変更しました:\n" + newTask).queue();
        tasks.save();
    }

    @Override
    public Collection<net.dv8tion.jda.api.interactions.commands.Command.Choice> getChoices(final CommandAutoCompleteInteractionEvent event, final ReservationReminder reservationReminder) {
        if (!event.getFocusedOption().getName().equals("id")) {
            return Command.super.getChoices(event, reservationReminder);
        }

        return reservationReminder.getReminderTasks().getReminderIdChoices(event.getFocusedOption().getValue());
    }
}
