package com.hamusuke.reminder.reminders;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class ReminderTasks implements Closeable {
    private static final String SAVE_FILE_NAME = "reservation_reminders.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().excludeFieldsWithoutExposeAnnotation().create();
    private final ScheduledExecutorService autoSaver = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "Auto Save Thread"));
    private final AtomicBoolean autoSaverStarted = new AtomicBoolean();
    private final Map<UUID, TwiceRemindTask> tasks = Maps.newConcurrentMap();

    private static boolean filterReminderByInput(final Map.Entry<UUID, TwiceRemindTask> e, final String input) {
        return input.isBlank()
                || e.getValue().getHyphenatedDuration().contains(input)
                || e.getKey().toString().startsWith(input);
    }

    public void add(final UUID uuid, final TwiceRemindTask task) {
        this.tasks.put(uuid, task);
    }

    public TwiceRemindTask remove(final UUID uuid) {
        return this.tasks.remove(uuid);
    }

    public boolean isEmpty() {
        return this.tasks.isEmpty();
    }

    public Set<Map.Entry<UUID, TwiceRemindTask>> entrySet() {
        return this.tasks.entrySet();
    }

    public void killAll() {
        this.tasks.values().forEach(TwiceRemindTask::kill);
    }

    public void removeDiedTasks() {
        this.tasks.values().removeIf(TwiceRemindTask::areAllRemindersDone);
    }

    public boolean has(final UUID uuid) {
        return this.tasks.containsKey(uuid);
    }

    public int size() {
        return this.tasks.size();
    }

    public List<Command.Choice> getReminderIdChoices(final String input) {
        return this.tasks.entrySet().stream()
                .filter(e -> filterReminderByInput(e, input))
                .limit(OptionData.MAX_CHOICES)
                .map(e ->
                        new Command.Choice(
                                e.getValue().getHyphenatedDuration() + "の予約", e.getKey().toString()))
                .toList();
    }

    public synchronized void startAutoSaveThread() {
        if (this.autoSaverStarted.get()) {
            return;
        }

        this.autoSaverStarted.set(true);
        this.autoSaver.scheduleAtFixedRate(this::save, 1L, 1L, TimeUnit.HOURS);
    }

    public UUID validateAndFind(final SlashCommandInteractionEvent event, final String uuidStr) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            event.reply("不正なIDです。").setEphemeral(true).queue();
            return null;
        }

        if (!this.has(uuid)) {
            event.reply("見つかりませんでした。IDを確認してください。").setEphemeral(true).queue();
            return null;
        }

        return uuid;
    }

    public synchronized void load(final Function<JsonObject, TwiceRemindTask> parser) {
        final var file = new File(SAVE_FILE_NAME);
        if (!file.exists()) {
            return;
        }

        try {
            final var obj = GSON.fromJson(new FileReader(SAVE_FILE_NAME, StandardCharsets.UTF_8), JsonObject.class);
            if (obj == null) {
                return;
            }

            for (final var e : obj.entrySet()) {
                final var id = UUID.fromString(e.getKey());
                this.tasks.put(id, parser.apply(e.getValue().getAsJsonObject()));
            }
        } catch (Exception e) {
            System.err.println("Failed to load reservation reminders: " + e.getMessage());
        }
    }

    public synchronized void save() {
        this.removeDiedTasks();
        try (final var w = GSON.newJsonWriter(new FileWriter(SAVE_FILE_NAME, StandardCharsets.UTF_8))) {
            w.beginObject();

            for (final var e : this.tasks.entrySet()) {
                w.name(e.getKey().toString());
                e.getValue().writeTo(w);
            }

            w.endObject();
        } catch (Exception e) {
            System.err.println("Failed to save reservation reminders: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        this.autoSaver.shutdownNow();
    }
}
