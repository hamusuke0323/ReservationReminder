package com.hamusuke.reminder.reminders.message;

import java.util.function.Supplier;

@FunctionalInterface
public interface FriendlyReminderMessage extends Supplier<String> {
}
