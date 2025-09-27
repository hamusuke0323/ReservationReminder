package com.hamusuke.reminder.modal;

import com.hamusuke.reminder.modal.modals.LoginFormModal;

public final class Modals {
    private final ModalDispatcher dispatcher = new ModalDispatcher();

    public Modals() {
        this.dispatcher.register(LoginFormModal.INSTANCE);
    }

    public ModalDispatcher getDispatcher() {
        return this.dispatcher;
    }
}
