package com.hamusuke.reminder.util;

import com.google.common.collect.Maps;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;

public final class EnvLoader {
    private static final String ENV_FILE_PATH = ".env";
    private static EnvLoader INSTANCE;
    private final Map<String, String> env = Maps.newHashMap();

    private EnvLoader() {
        if (INSTANCE != null) {
            throw new IllegalStateException("Already initialized");
        }

        INSTANCE = this;
        this.load();
    }

    public static synchronized void make(final Set<String> keys) {
        final var file = new File(ENV_FILE_PATH);

        try {
            Files.write(file.toPath(), keys.stream().map(key -> key + "=").toList(), StandardOpenOption.CREATE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getEnv(String key) {
        return getInstance().env.get(key);
    }

    private static EnvLoader getInstance() {
        if (INSTANCE == null) {
            new EnvLoader();
        }

        return INSTANCE;
    }

    private synchronized void load() {
        final var file = new File(ENV_FILE_PATH);
        if (!file.exists()) {
            return;
        }

        try {
            final var lines = Files.readAllLines(file.toPath());
            for (final var line : lines) {
                final var split = line.split("=", 2);
                if (split.length != 2) {
                    continue;
                }

                this.env.put(split[0], split[1]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
