package com.hamusuke.reminder;

import com.hamusuke.reminder.command.Commands;
import com.hamusuke.reminder.event.EventListener;
import com.hamusuke.reminder.event.ReminderScheduler;
import com.hamusuke.reminder.modal.Modals;
import com.hamusuke.reminder.profiler.DebugProfiler;
import com.hamusuke.reminder.reminders.ReminderTasks;
import com.hamusuke.reminder.web.CampusWeb;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Optional;

public final class ReservationReminder {
    private final JDA jda;
    @Nullable
    private CampusWeb campusWeb;
    private final String channelId;
    private final String roleId;
    private final Commands commands = new Commands();
    private final Modals modals = new Modals();
    private final ReminderTasks reminderTasks = new ReminderTasks();
    private final DebugProfiler debugProfiler = new DebugProfiler();

    ReservationReminder(final String token, final String channelId, final String roleId) {
        this.channelId = channelId;
        this.roleId = roleId;
        this.jda = JDABuilder.createLight(token, EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                .addEventListeners(
                        new EventListener(this),
                        new ReminderScheduler(this))
                .build();
        this.jda.updateCommands().addCommands(this.commands.getDispatcher().getAllCommandData()).queue();
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "Reservation Reminder Shutdown Thread"));
    }

    private void shutdown() {
        System.out.println("Saving reminders...");
        this.reminderTasks.save();
        System.out.println("Done");

        System.out.println("Killing Reminder Tasks...");
        this.reminderTasks.killAll();
        System.out.println("Done");

        System.out.println("Shutting down...");
        this.jda.shutdown();
        System.out.println("Done");
    }

    public JDA getJDA() {
        return this.jda;
    }

    public Optional<CampusWeb> getCampusWeb() {
        return Optional.ofNullable(this.campusWeb);
    }

    public String getChannelId() {
        return this.channelId;
    }

    public String getRoleId() {
        return this.roleId;
    }

    public void login(final CampusWeb web) {
        this.campusWeb = web;
    }

    public synchronized void closeCampusWebAndNullify() {
        this.getCampusWeb().ifPresent(CampusWeb::close);
        this.campusWeb = null;
    }

    public ReminderTasks getReminderTasks() {
        return this.reminderTasks;
    }

    public DebugProfiler getDebugProfiler() {
        return this.debugProfiler;
    }

    public Commands getCommands() {
        return this.commands;
    }

    public Modals getModals() {
        return this.modals;
    }
}
