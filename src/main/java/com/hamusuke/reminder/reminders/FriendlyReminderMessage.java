package com.hamusuke.reminder.reminders;

import com.hamusuke.reminder.util.DiscordChatFormatUtil;

import java.util.function.Supplier;

@FunctionalInterface
public interface FriendlyReminderMessage extends Supplier<String> {
    final class First implements FriendlyReminderMessage {
        private final String message;

        private First(final String message) {
            this.message = message;
        }

        public static First mentionEveryoneWhoHas(final String roleId) {
            return new First(DiscordChatFormatUtil.toMentionFormat(roleId) + " 予約書を書きに行ってください。");
        }

        @Override
        public String get() {
            return this.message;
        }
    }

    final class Second implements FriendlyReminderMessage {
        private final String message;

        private Second(final String message) {
            this.message = message;
        }

        public static Second mentionEveryoneWhoHas(final String roleId) {
            return new Second(DiscordChatFormatUtil.toMentionFormat(roleId) + " 予約書を受け取り警備室に提出しに行ってください。");
        }

        @Override
        public String get() {
            return this.message;
        }
    }
}
