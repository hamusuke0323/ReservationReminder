package com.hamusuke.reminder.command.commands;

import com.hamusuke.reminder.command.Command;
import com.hamusuke.reminder.command.CommandContext;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public final class LoginCommand implements Command {
    public static final LoginCommand INSTANCE = new LoginCommand();

    private LoginCommand() {
    }

    @Override
    public String getName() {
        return "login";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash(this.getName(), "学務システムにログイン");
    }

    @Override
    public void execute(final CommandContext context) {
        if (context.event().getUser().isBot()) {
            return;
        }

        final var userId = context.event().getUser().getId();
        context.event().replyModal(context.reservationReminder()
                .getModals().getDispatcher().createModal("login", userId)).queue();
    }
}
