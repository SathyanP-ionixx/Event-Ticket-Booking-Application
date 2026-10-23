package com.ionixx.Event_Ticket_Booking.integration;

import com.ionixx.Event_Ticket_Booking.dto.BookingRequest;
import com.ionixx.Event_Ticket_Booking.dto.BookingResponse;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the PESSIMISTIC_WRITE lock on the event row: several customers racing for the same seat
 * must end with exactly one CONFIRMED booking. Runs against real PostgreSQL row locks.
 */
class BookingConcurrencyIT extends AbstractApiIT {

    @Autowired BookingService bookingService;

    @Test
    void sameSeatRequestedConcurrently_onlyOneBookingIsConfirmed() throws Exception {
        Event event = saveEvent("Concurrency Fest", inDays(10), 100, "500");
        int threads = 4;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<BookingResponse>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                BookingRequest r = new BookingRequest();
                r.setEventId(event.getId());
                r.setCustomerName("Customer Number");
                r.setCustomerEmail("user" + n + "@example.com");
                r.setSeatNumbers(List.of(7));
                ready.countDown();
                go.await();
                return bookingService.createBooking(r);
            }));
        }
        ready.await(10, TimeUnit.SECONDS);
        go.countDown();

        int confirmed = 0;
        int rejected = 0;
        try {
            for (Future<BookingResponse> f : futures) {
                BookingResponse b = f.get(30, TimeUnit.SECONDS);
                if ("CONFIRMED".equals(b.getStatus())) {
                    confirmed++;
                } else {
                    rejected++;
                    assertEquals("Seats already booked: 7", b.getRejectionReason());
                }
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, confirmed, "exactly one booking may win the seat");
        assertEquals(threads - 1, rejected);
        assertEquals(List.of(7), bookingSeatRepository.findConfirmedSeatNumbers(event.getId(), List.of(7)));
        assertTrue(bookingRepository.count() == threads, "rejected bookings are saved too");
    }
}
