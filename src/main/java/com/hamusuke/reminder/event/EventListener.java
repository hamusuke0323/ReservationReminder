package com.hamusuke.reminder.event;

import com.hamusuke.reminder.ReservationReminder;
import com.hamusuke.reminder.command.CommandContext;
import com.hamusuke.reminder.reminders.TwiceRemindTask;
import com.hamusuke.reminder.reminders.message.MentionedReminderMessage;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

public final class EventListener extends ListenerAdapter {
    private final ReservationReminder reservationReminder;

    public EventListener(final ReservationReminder reservationReminder) {
        this.reservationReminder = reservationReminder;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        this.reservationReminder.getCommands().getDispatcher()
                .replyAutoCompleteChoices(event, this.reservationReminder);
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        this.reservationReminder.getReminderTasks().load(jsonObject ->
                TwiceRemindTask.from(jsonObject, this.reservationReminder.getJDA(),
                        this.reservationReminder.getChannelId(),
                        MentionedReminderMessage.First.forMemberWithRole(this.reservationReminder.getRoleId()),
                        MentionedReminderMessage.Second.forMemberWithRole(this.reservationReminder.getRoleId())));

        this.reservationReminder.getReminderTasks().startAutoSaveThread();
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        this.reservationReminder.getCommands().getDispatcher()
                .execute(new CommandContext(event, this.reservationReminder));
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        this.reservationReminder.getModals().getDispatcher()
                .interact(event, this.reservationReminder);
    }
}
