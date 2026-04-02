package com.hamusuke.reminder.reminders.message;

import com.hamusuke.reminder.util.DiscordChatFormatUtil;

public class MentionedReminderMessage implements FriendlyReminderMessage {
    private final String formattedMessage;

    public MentionedReminderMessage(final String roleId, final String message) {
        this.formattedMessage = DiscordChatFormatUtil.toRoleMentionFormat(roleId) + " " + message;
    }

    @Override
    public final String get() {
        return this.formattedMessage;
    }

    public static final class First extends MentionedReminderMessage {
        private static final String FIRST_MESSAGE = "予約書を書きに行ってください。";

        private First(final String roleId, final String message) {
            super(roleId, message);
        }

        public static MentionedReminderMessage.First forMemberWithRole(final String roleId) {
            return new MentionedReminderMessage.First(roleId, FIRST_MESSAGE);
        }
    }

    public static final class Second extends MentionedReminderMessage {
        private static final String SECOND_MESSAGE = "予約書を受け取り警備室に提出しに行ってください。";

        private Second(final String roleId, final String message) {
            super(roleId, message);
        }

        public static MentionedReminderMessage.Second forMemberWithRole(final String roleId) {
            return new MentionedReminderMessage.Second(roleId, SECOND_MESSAGE);
        }
    }
}
