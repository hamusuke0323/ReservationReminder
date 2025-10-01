package com.hamusuke.reminder.reminders;

import com.google.common.collect.Lists;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import com.hamusuke.reminder.reminders.message.FriendlyReminderMessage;
import net.dv8tion.jda.api.JDA;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public final class TwiceRemindTask {
    private final String hyphenatedDuration;
    private final Reminder firstReminder;
    private final Reminder secondReminder;

    public TwiceRemindTask(final String hyphenatedDuration, final Reminder firstReminder, final Reminder secondReminder) {
        this.hyphenatedDuration = hyphenatedDuration;
        this.firstReminder = firstReminder;
        this.secondReminder = secondReminder;
    }

    public static TwiceRemindTask from(final JsonObject obj, final JDA jda, final String channelId, final FriendlyReminderMessage firstMsg, final FriendlyReminderMessage secondMsg) {
        return new TwiceRemindTask(
                obj.get("duration").getAsString(),
                Reminder.parse(obj.get("first").getAsString(), jda, channelId, firstMsg),
                Reminder.parse(obj.get("second").getAsString(), jda, channelId, secondMsg));
    }

    public TwiceRemindTask withNewRemindTimes(final LocalDateTime firstRemindTime, final LocalDateTime secondRemindTime) {
        return new TwiceRemindTask(this.hyphenatedDuration,
                this.firstReminder.withNewRemindTime(firstRemindTime),
                this.secondReminder.withNewRemindTime(secondRemindTime));
    }

    public void kill() {
        this.firstReminder.kill();
        this.secondReminder.kill();
    }

    public boolean areAllRemindersDone() {
        return this.firstReminder.isDone() && this.secondReminder.isDone();
    }

    public String getHyphenatedDuration() {
        return this.hyphenatedDuration;
    }

    public void writeTo(final JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("duration").value(this.hyphenatedDuration);
        writer.name("first").value(this.firstReminder.formatRemindTime());
        writer.name("second").value(this.secondReminder.formatRemindTime());
        writer.endObject();
    }

    @Override
    public String toString() {
        return "- 最初のリマインド: " +
                this.firstReminder.toString() +
                "\n- 2番目のリマインド: " +
                this.secondReminder.toString();
    }

    public static final class TwiceRemindTaskBuilder {
        private final JDA jda;
        private final String channelId;
        private final String hyphenatedDuration;
        private final List<Reminder> reminders = Lists.newArrayList();

        private TwiceRemindTaskBuilder(final JDA jda, final String channelId, final String hyphenatedDuration) {
            this.jda = jda;
            this.channelId = channelId;
            this.hyphenatedDuration = hyphenatedDuration;
        }

        public static TwiceRemindTaskBuilder of(final JDA jda, final String channelId, final String hyphenatedDuration) {
            return new TwiceRemindTaskBuilder(jda, channelId, hyphenatedDuration);
        }

        public RemindBuilder start() {
            return this.new RemindBuilder();
        }

        public TwiceRemindTask build() {
            if (this.reminders.size() < 2) {
                throw new IllegalStateException("Twice remind task needs two reminders.");
            }

            return new TwiceRemindTask(this.hyphenatedDuration, this.reminders.get(0), this.reminders.get(1));
        }

        private void push(final Reminder reminder) {
            this.reminders.add(reminder);
        }

        public class RemindBuilder {
            private LocalDateTime at;
            private FriendlyReminderMessage message;

            public RemindBuilder remindAt(final LocalDateTime at) {
                this.at = at;
                return this;
            }

            public RemindBuilder withMessage(final FriendlyReminderMessage message) {
                this.message = message;
                return this;
            }

            private void build() {
                TwiceRemindTaskBuilder.this.push(new Reminder(TwiceRemindTaskBuilder.this.jda, TwiceRemindTaskBuilder.this.channelId, Objects.requireNonNull(this.message, "message"), Objects.requireNonNull(this.at, "at")));
            }

            public RemindBuilder then() {
                this.build();
                return TwiceRemindTaskBuilder.this.new RemindBuilder();
            }

            public TwiceRemindTaskBuilder finish() {
                this.build();
                return TwiceRemindTaskBuilder.this;
            }
        }
    }
}
