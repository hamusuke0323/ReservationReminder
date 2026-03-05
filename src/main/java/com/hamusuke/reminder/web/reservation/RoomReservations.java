package com.hamusuke.reminder.web.reservation;

import com.hamusuke.reminder.throwable.QueryFailedException;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public record RoomReservations(String roomName, List<RoomReservation> reservations) {
    private static final String ATTR_VALUE = "kyuko-shi-jugyo";
    private static final int MINUTES_PER_COLSPAN = 10;

    public static RoomReservations parseFrom(final String roomName, final LocalDate date, final Element body) {
        final var tds = body.select("td");
        if (tds.isEmpty()) {
            throw new QueryFailedException("query td returned empty table!");
        }

        List<Node> f1MeetingRoom = new ArrayList<>();
        for (final var td : tds) {
            if (td.text().equalsIgnoreCase(roomName) && td.parent() != null) {
                f1MeetingRoom.addAll(td.parent().childNodes());
                break;
            }
        }

        f1MeetingRoom.removeIf(node -> node instanceof TextNode);
        if (f1MeetingRoom.size() < 2) {
            return new RoomReservations(roomName, List.of());
        }

        f1MeetingRoom.remove(0);
        f1MeetingRoom.remove(f1MeetingRoom.size() - 1);

        List<RoomReservation> reservations = new ArrayList<>();
        LocalDateTime now = date.atTime(6, 0);

        for (final var node : f1MeetingRoom) {
            if (node.attributes().get("class").equals(ATTR_VALUE)) {
                int colspan = Integer.parseInt(node.attributes().get("colspan"));
                String reason = node.toString();
                if (node.childNode(0) instanceof TextNode textNode) {
                    reason = textNode.text();
                }

                reservations.add(new RoomReservation(reason, now, now = now.plusMinutes((long) colspan * MINUTES_PER_COLSPAN)));
                continue;
            }

            now = now.plusMinutes(MINUTES_PER_COLSPAN);
        }

        return new RoomReservations(roomName, List.copyOf(reservations));
    }

    /**
     *
     * @param start start time
     * @param end   end time
     * @return reservation or null if we can reserve
     */
    @Nullable
    public RoomReservation findOtherReservation(final LocalDateTime start, final LocalDateTime end) {
        for (final var reservation : this.reservations) {
            if (reservation.cannotReserve(start, end)) {
                return reservation;
            }
        }

        return null;
    }
}
