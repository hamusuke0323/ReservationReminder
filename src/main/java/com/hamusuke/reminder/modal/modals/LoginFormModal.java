package com.hamusuke.reminder.modal.modals;

import com.hamusuke.reminder.modal.Modal;
import com.hamusuke.reminder.modal.ModalContext;
import com.hamusuke.reminder.throwable.LoginFailedException;
import com.hamusuke.reminder.web.CampusWeb;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.internal.interactions.component.TextInputImpl;

public final class LoginFormModal implements Modal {
    public static final LoginFormModal INSTANCE = new LoginFormModal();

    private LoginFormModal() {
    }

    @Override
    public String getName() {
        return "login";
    }

    @Override
    public net.dv8tion.jda.api.interactions.modals.Modal create(final String userId) {
        return net.dv8tion.jda.api.interactions.modals.Modal.create(userId + ":" + this.getName(), "ログイン")
                .addActionRow(new TextInputImpl("sid", TextInputStyle.SHORT, "ユーザー名", -1, -1, true, null, "ユーザー名"))
                .addActionRow(new TextInputImpl("pass", TextInputStyle.SHORT, "パスワード", -1, -1, true, null, "パスワード"))
                .build();
    }

    @Override
    public void handle(final ModalContext context) {
        final var event = context.event();
        event.deferReply(true).queue();
        final var hook = event.getHook();
        hook.setEphemeral(true);

        var sid = "";
        var pass = "";

        for (final var m : event.getValues()) {
            switch (m.getId()) {
                case "sid":
                    sid = m.getAsString();
                    break;
                case "pass":
                    pass = m.getAsString();
                    break;
            }
        }

        final var web = new CampusWeb();

        try {
            final var name = web.login(sid, pass);
            hook.sendMessage("ログイン成功！" + "ようこそ " + name + " さん").queue();
            context.reservationReminder().closeCampusWebAndNullify();
            context.reservationReminder().login(web);
        } catch (LoginFailedException e) {
            hook.sendMessage("ログイン失敗！").queue();
            web.close();
        }
    }
}
