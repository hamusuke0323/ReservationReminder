package com.hamusuke.reminder.command.commands;

import com.hamusuke.reminder.ReservationReminder;
import com.hamusuke.reminder.command.Command;
import com.hamusuke.reminder.command.CommandContext;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.Collection;

public class RecheckCommand implements Command {
    public static final RecheckCommand INSTANCE = new RecheckCommand();

    private RecheckCommand() {
    }

    @Override
    public String getName() {
        return "recheck";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash(this.getName(), "施設が利用可能かどうか再チェックします");
    }

    @Override
    public void execute(CommandContext context) {
        context.event().deferReply(true).queue();
        final var hook = context.event().getHook();

    }

    @Override
    public Collection<Choice> getChoices(CommandAutoCompleteInteractionEvent event, ReservationReminder reservationReminder) {
        return Command.super.getChoices(event, reservationReminder);
    }
}
