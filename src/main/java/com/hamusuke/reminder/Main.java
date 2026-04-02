package com.hamusuke.reminder;

import com.hamusuke.reminder.util.EnvLoader;

import java.util.Set;

public final class Main {
    private static final String TOKEN_KEY = "TOKEN";
    private static final String CHANNEL_ID_KEY = "CHANNEL_ID";
    private static final String ROLE_ID_KEY = "ROLE_ID";

    public static void main(String[] args) {
        final var token = EnvLoader.getEnv(TOKEN_KEY);
        final var channelId = EnvLoader.getEnv(CHANNEL_ID_KEY);
        final var roleId = EnvLoader.getEnv(ROLE_ID_KEY);

        if (token == null || channelId == null || roleId == null || token.isBlank() || channelId.isBlank() || roleId.isBlank()) {
            System.err.printf("%s, %s and %s is not set in .env file.\n", TOKEN_KEY, CHANNEL_ID_KEY, ROLE_ID_KEY);
            EnvLoader.make(Set.of(TOKEN_KEY, CHANNEL_ID_KEY, ROLE_ID_KEY));
            return;
        }

        new ReservationReminder(token, channelId, roleId);
    }
}
