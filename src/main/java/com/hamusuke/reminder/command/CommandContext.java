package com.hamusuke.reminder.command;

import com.hamusuke.reminder.ReservationReminder;
import com.hamusuke.reminder.web.CampusWeb;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.Optional;

public record CommandContext(SlashCommandInteractionEvent event, ReservationReminder reservationReminder, JDA jda,
                             Optional<CampusWeb> campusWeb, String channelId, String roleId) {
    public CommandContext(final SlashCommandInteractionEvent event, final ReservationReminder reservationReminder) {
        this(event, reservationReminder, reservationReminder.getJDA(), reservationReminder.getCampusWeb(), reservationReminder.getChannelId(), reservationReminder.getRoleId());
    }
}
