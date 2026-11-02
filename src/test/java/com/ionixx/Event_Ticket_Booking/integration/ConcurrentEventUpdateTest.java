package com.ionixx.Event_Ticket_Booking.integration;

import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.repository.BookingSeatRepository;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * An organiser shrinks the venue while a customer books a seat that would no longer exist. Both take the
 * event row lock, so exactly one wins and the other sees the result: a confirmed seat can never end up
 * beyond the event's total_seats.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ConcurrentEventUpdateTest extends AbstractIntegrationTest {

    @Autowired EventRepository eventRepository;
    @Autowired BookingSeatRepository bookingSeatRepository;

    @RepeatedTest(5)
    void shrinkingTheVenueWhileSeat25IsBeingBooked_neverLeavesABookedSeatOutsideTheEvent() throws Exception {
        Account organiser = organiser();
        long eventId = createEvent(organiser, 30);
        Account customer = customer();
        String shrinkTo20 = eventJson(uniqueEventName(), LocalDate.now().plusDays(45).toString(), 20, "100.00");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Future<Integer> update = pool.submit(() -> {
                go.await();
                return mvc.perform(as(put("/api/events/" + eventId), organiser)
                                .contentType(MediaType.APPLICATION_JSON).content(shrinkTo20))
                        .andReturn().getResponse().getStatus();
            });
            Future<MvcResult> booking = pool.submit(() -> {
                go.await();
                return book(customer, eventId, "John Doe", uniqueEmail(), 25).andReturn();
            });
            go.countDown();

            int updateStatus = update.get(30, TimeUnit.SECONDS);
            MvcResult bookingResult = booking.get(30, TimeUnit.SECONDS);
            int bookingStatus = bookingResult.getResponse().getStatus();
            String bookingBody = bookingResult.getResponse().getContentAsString();

            Event event = eventRepository.findById(eventId).orElseThrow();
            List<Integer> sold = bookingSeatRepository.findAllConfirmedSeatNumbers(eventId);

            boolean seatWasSold = bookingStatus == 201 && "CONFIRMED".equals(JsonPath.read(bookingBody, "$.status"));
            if (seatWasSold) {
                // the booking took the lock first: the shrink must then be refused
                assertThat(updateStatus).as("shrink after seat 25 was sold").isEqualTo(400);
                assertThat(event.getTotalSeats()).isEqualTo(30);
                assertThat(sold).containsExactly(25);
            } else {
                // the update took the lock first: seat 25 no longer exists, so the booking is a validation error
                assertThat(updateStatus).as("shrink before any seat was sold").isEqualTo(200);
                assertThat(bookingStatus).as("booking of a seat that no longer exists").isEqualTo(400);
                assertThat(event.getTotalSeats()).isEqualTo(20);
                assertThat(sold).isEmpty();
            }
            assertThat(sold).allMatch(seat -> seat <= event.getTotalSeats());
        } finally {
            pool.shutdownNow();
        }
    }
}
