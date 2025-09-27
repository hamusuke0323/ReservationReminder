package com.hamusuke.reminder;

public final class Main {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("<token> <channel_id> <role_id>");
            return;
        }

        new ReservationReminder(args[0], args[1], args[2]);
    }
}
