package com.hamusuke.reminder.command.commands;

import com.hamusuke.reminder.command.Command;
import com.hamusuke.reminder.command.CommandContext;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public final class LogoutCommand implements Command {
    public static final LogoutCommand INSTANCE = new LogoutCommand();

    private LogoutCommand() {
    }

    @Override
    public String getName() {
        return "logout";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash(this.getName(), "ログアウト");
    }

    @Override
    public void execute(final CommandContext context) {
        context.event().deferReply(true).queue();
        final var hook = context.event().getHook();
        hook.setEphemeral(true);
        if (context.campusWeb().isEmpty()) {
            hook.sendMessage("ログインしていません").queue();
            return;
        }

        try {
            context.campusWeb().get().logout();
            hook.sendMessage("ログアウトしました").queue();
        } catch (Exception e) {
            hook.sendMessage("内部エラー").queue();
        } finally {
            context.reservationReminder().closeCampusWebAndNullify();
        }
    }
}
