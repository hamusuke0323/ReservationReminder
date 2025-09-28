package com.hamusuke.reminder.command;

import com.google.common.collect.Maps;
import com.hamusuke.reminder.ReservationReminder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.Map;

public final class CommandDispatcher {
    private final Map<String, Command> commands = Maps.newHashMap();

    public CommandData[] getAllCommandData() {
        return this.commands.values().stream().map(Command::getCommandData).toArray(CommandData[]::new);
    }

    public void register(final Command command) {
        if (this.commands.containsKey(command.getName())) {
            throw new IllegalArgumentException("Command with name " + command.getName() + " is already registered");
        }

        this.commands.put(command.getName(), command);
    }

    public synchronized void execute(final CommandContext context) {
        final var event = context.event();
        if (!this.commands.containsKey(event.getName())) {
            event.reply("不明なコマンドです").setEphemeral(true).queue();
            return;
        }

        final var command = this.commands.get(event.getName());
        if (command.shouldExecuteInSpecifiedChannel() && !context.reservationReminder().getChannelId().equals(event.getChannelId())) {
            event.reply("指定されたチャンネル外からコマンドを送信することはできません。").setEphemeral(true).queue();
            return;
        }

        command.execute(context);
    }

    public void replyAutoCompleteChoices(final CommandAutoCompleteInteractionEvent event, final ReservationReminder reservationReminder) {
        final var command = this.commands.get(event.getName());
        if (command == null) {
            return;
        }

        final var choices = command.getChoices(event, reservationReminder);
        if (choices.isEmpty()) {
            return;
        }

        event.replyChoices(choices).queue();
    }
}
