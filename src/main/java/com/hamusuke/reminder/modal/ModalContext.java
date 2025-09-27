package com.hamusuke.reminder.modal;

import com.hamusuke.reminder.ReservationReminder;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;

public record ModalContext(ModalInteractionEvent event, ReservationReminder reservationReminder) {
}
