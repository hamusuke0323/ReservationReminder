package com.hamusuke.reminder.command.commands;

import com.hamusuke.reminder.ReservationReminder;
import com.hamusuke.reminder.command.Command;
import com.hamusuke.reminder.command.CommandContext;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.Collection;

public final class ClearCommand implements Command {
    public static final ClearCommand INSTANCE = new ClearCommand();

    private ClearCommand() {
    }

    @Override
    public String getName() {
        return "clear";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash(this.getName(), "指定したリマインドを解除します")
                .addOption(OptionType.STRING, "id", "リマインドID", true, true);
    }

    @Override
    public void execute(final CommandContext context) {
        final var event = context.event();
        final var id = event.getOption("id", OptionMapping::getAsString);
        if (id == null) {
            event.reply("ID or first was not specified").setEphemeral(true).queue();
            return;
        }

        final var tasks = context.reservationReminder().getReminderTasks();
        final var uuid = tasks.validateAndFind(context.event(), id);
        if (uuid == null) {
            return;
        }

        final var removed = tasks.remove(uuid);
        removed.kill();
        event.reply(removed.getHyphenatedDuration() + "の予約のためのリマインドを解除しました。").queue();
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
