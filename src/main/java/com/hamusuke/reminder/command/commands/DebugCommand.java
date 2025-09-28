package com.hamusuke.reminder.command.commands;

import com.google.common.collect.Lists;
import com.hamusuke.reminder.command.Command;
import com.hamusuke.reminder.command.CommandContext;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.utils.FileUpload;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class DebugCommand implements Command {
    public static final DebugCommand INSTANCE = new DebugCommand();

    private DebugCommand() {
    }

    public static List<String> splitEvery(String text, int size) {
        List<String> result = Lists.newArrayList();
        for (int i = 0; i < text.length(); i += size) {
            int end = Math.min(i + size, text.length());
            result.add(text.substring(i, end));
        }

        return result;
    }

    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash(this.getName(), "デバッグ情報を出力します");
    }

    @Override
    public void execute(final CommandContext context) {
        final var text = context.reservationReminder().getDebugProfiler().toString();
        if (text.isBlank()) {
            context.event().reply("現在デバッグ情報はありません").setEphemeral(true).queue();
            return;
        }

        context.event().deferReply(true).queue();
        final var hook = context.event().getHook();
        hook.setEphemeral(true);
        hook.sendFiles(FileUpload.fromData(text.getBytes(StandardCharsets.UTF_8), "debug.txt"))
                .queue(ignored -> {
                }, t -> splitEvery("# Debug Info\n" + text, Message.MAX_CONTENT_LENGTH)
                        .forEach(s -> hook.sendMessage(s).queue()));
    }

    @Override
    public boolean shouldExecuteInSpecifiedChannel() {
        return false;
    }
}
