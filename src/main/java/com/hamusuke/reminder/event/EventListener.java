package com.hamusuke.reminder.event;

import com.hamusuke.reminder.ReservationReminder;
import com.hamusuke.reminder.command.CommandContext;
import com.hamusuke.reminder.reminders.FriendlyReminderMessage;
import com.hamusuke.reminder.reminders.TwiceRemindTask;
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
        this.reservationReminder.getReminderTasks().load(o ->
                TwiceRemindTask.from(o, this.reservationReminder.getJDA(),
                        this.reservationReminder.getChannelId(),
                        FriendlyReminderMessage.First.mentionEveryoneWhoHas(this.reservationReminder.getRoleId()),
                        FriendlyReminderMessage.Second.mentionEveryoneWhoHas(this.reservationReminder.getRoleId())));

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
