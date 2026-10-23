package com.ionixx.Event_Ticket_Booking.util;

import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.InvalidRequestException;

public class IDparser {


    public static long parsePositiveId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) {
                throw new InvalidRequestException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new InvalidRequestException();
        }
    }

}
