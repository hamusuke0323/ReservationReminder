package com.hamusuke.reminder.modal;

public interface Modal {
    String getName();

    net.dv8tion.jda.api.interactions.modals.Modal create(final String userId);

    void handle(final ModalContext context);
}
