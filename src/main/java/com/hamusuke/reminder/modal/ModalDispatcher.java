package com.hamusuke.reminder.modal;

import com.google.common.collect.Maps;
import com.hamusuke.reminder.ReservationReminder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;

import java.util.Map;

public final class ModalDispatcher {
    private final Map<String, Modal> modals = Maps.newHashMap();

    public void register(final Modal modal) {
        if (this.modals.containsKey(modal.getName())) {
            throw new IllegalArgumentException("Modal with name " + modal.getName() + " is already registered");
        }

        this.modals.put(modal.getName(), modal);
    }

    public net.dv8tion.jda.api.interactions.modals.Modal createModal(final String modalName, final String userId) {
        final var modal = this.modals.get(modalName);
        if (modal == null) {
            throw new IllegalArgumentException("Modal with name " + modalName + " not found");
        }

        return modal.create(userId);
    }

    public void interact(final ModalInteractionEvent event, final ReservationReminder reservationReminder) {
        final var modalId = event.getModalId().split(":");
        final var userId = modalId[0];
        final var op = modalId[1];
        final var modal = this.modals.get(op);

        if (modal == null || !userId.equals(event.getUser().getId())) {
            event.reply("Error occurred!").setEphemeral(true).queue();
            return;
        }

        modal.handle(new ModalContext(event, reservationReminder));
    }
}
