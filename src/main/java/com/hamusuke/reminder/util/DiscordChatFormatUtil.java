package com.hamusuke.reminder.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class DiscordChatFormatUtil {
    public static String toRoleMentionFormat(final String roleId) {
        return "<@&" + roleId + ">";
    }

    public static String toTimestampRelative(final LocalDateTime time) {
        return toTimestampFormat(time, "R");
    }

    public static String toTimestampFormat(final LocalDateTime time, final String format) {
        return "<t:" + time.atZone(ZoneId.systemDefault()).toEpochSecond() + ":" + format + ">";
    }
}
