package com.hamusuke.reminder.command;

import com.hamusuke.reminder.command.commands.*;

public final class Commands {
    private final CommandDispatcher dispatcher = new CommandDispatcher();

    public Commands() {
        this.dispatcher.register(ChangeTimeCommand.INSTANCE);
        this.dispatcher.register(ClearCommand.INSTANCE);
        this.dispatcher.register(ListCommand.INSTANCE);
        this.dispatcher.register(LoginCommand.INSTANCE);
        this.dispatcher.register(LogoutCommand.INSTANCE);
    }

    public CommandDispatcher getDispatcher() {
        return this.dispatcher;
    }
}
