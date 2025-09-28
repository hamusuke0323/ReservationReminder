package com.hamusuke.reminder.command.commands;

import com.hamusuke.reminder.command.Command;
import com.hamusuke.reminder.command.CommandContext;
import net.dv8tion.jda.api.entities.Message;
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

        context.event().deferReply().queue();
        final var hook = context.event().getHook();
        final var b = new StringBuilder("リマインド一覧\n");
        for (final var entry : tasks.entrySet()) {
            final var append = "## " + entry.getValue().getHyphenatedDuration()
                    + "\n" + entry.getValue() + "\n"
                    + "- リマインドID: `" + entry.getKey()
                    + "`\n";

            if (b.length() + append.length() > Message.MAX_CONTENT_LENGTH) {
                hook.sendMessage(b.toString()).queue();
                b.setLength(0);
            }
            b.append(append);
        }

        hook.sendMessage(b.toString()).queue();
    }
}
