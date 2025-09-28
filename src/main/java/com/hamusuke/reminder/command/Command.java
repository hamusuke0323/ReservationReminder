package com.hamusuke.reminder.command;

import com.hamusuke.reminder.ReservationReminder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.Collection;
import java.util.Collections;

public interface Command {
    String getName();

    CommandData getCommandData();

    void execute(final CommandContext context);

    default Collection<net.dv8tion.jda.api.interactions.commands.Command.Choice> getChoices(final CommandAutoCompleteInteractionEvent event, final ReservationReminder reservationReminder) {
        return Collections.emptyList();
    }
}
