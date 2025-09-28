package com.hamusuke.reminder.command.commands;

import com.hamusuke.reminder.command.Command;
import com.hamusuke.reminder.command.CommandContext;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public final class ListCommand implements Command {
    public static final ListCommand INSTANCE = new ListCommand();

    private ListCommand() {
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash(this.getName(), "リマインド一覧を表示します");
    }

    @Override
    public void execute(final CommandContext context) {
        final var tasks = context.reservationReminder().getReminderTasks();
        if (tasks.isEmpty()) {
            context.event().reply("リマインドはありません。").queue();
            return;
        }

        final var b = new StringBuilder("リマインド一覧\n");
        for (final var entry : tasks.entrySet()) {
            b.append("## ")
                    .append(entry.getValue().getHyphenatedDuration())
                    .append("\n")
                    .append(entry.getValue())
                    .append("\n")
                    .append("- リマインドID: `")
                    .append(entry.getKey())
                    .append("`\n");
        }

        context.event().reply(b.toString()).queue();
    }
}
