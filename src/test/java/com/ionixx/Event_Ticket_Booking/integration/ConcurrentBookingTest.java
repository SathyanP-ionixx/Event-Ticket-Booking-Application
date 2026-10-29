package com.ionixx.Event_Ticket_Booking.integration;

import com.ionixx.Event_Ticket_Booking.entity.Booking;
import com.ionixx.Event_Ticket_Booking.repository.BookingRepository;
import com.ionixx.Event_Ticket_Booking.repository.BookingSeatRepository;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Many requests hit the same event at the same instant. Whatever the interleaving, the invariants must hold:
 * no seat is sold twice, nobody holds more than 6 seats, and every request is still answered with a clean 201
 * (CONFIRMED or REJECTED), never a 409/500 leaking out of the database.
 *
 * Race tests are probabilistic: a failure is proof of a bug, a pass is strong (not absolute) evidence.
 * Thread counts stay at 10 or below because each in-flight request holds one connection of the default
 * 10-connection pool while it waits for the event lock.
 */
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class ConcurrentBookingTest extends AbstractIntegrationTest {

    private static final String LIMIT_REASON = "Customer already holds the maximum seats for this event";

    @Autowired BookingRepository bookingRepository;
    @Autowired BookingSeatRepository bookingSeatRepository;
    @Autowired EventRepository eventRepository;
    @Autowired PlatformTransactionManager transactionManager;

    private record Outcome(int httpStatus, String bookingStatus, String reason) {}

    // ------------------------------------------------------------------ the race itself

    @RepeatedTest(3)
    void tenCustomersRaceForTheSameSeat_exactlyOneWins() throws Exception {
        long eventId = createEvent(organiser(), 10);
        Account customer = customer();

        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String email = uniqueEmail();
            tasks.add(() -> attempt(customer, eventId, email, 1));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertEveryAttemptWasAnswered(outcomes);
        assertThat(count(outcomes, "CONFIRMED")).as("winners").isEqualTo(1);
        assertThat(count(outcomes, "REJECTED")).as("losers").isEqualTo(9);
        assertThat(outcomes).filteredOn(o -> "REJECTED".equals(o.bookingStatus()))
                .extracting(Outcome::reason).containsOnly("Seats already booked: 1");

        assertThat(bookingSeatRepository.findAllConfirmedSeatNumbers(eventId)).containsExactly(1);
        assertThat(bookingRepository.countByEventIdAndStatus(eventId, "CONFIRMED")).isEqualTo(1);
        assertThat(bookingRepository.countByEventIdAndStatus(eventId, "REJECTED")).isEqualTo(9);
    }

    @Test
    void overlappingSeatSets_noSeatIsEverSoldTwice() throws Exception {
        long eventId = createEvent(organiser(), 30);
        Account customer = customer();

        // customer i asks for seats i, i+1, i+2: every neighbour overlaps with the next two
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            String email = uniqueEmail();
            int first = i;
            tasks.add(() -> attempt(customer, eventId, email, first, first + 1, first + 2));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertEveryAttemptWasAnswered(outcomes);
        long confirmed = count(outcomes, "CONFIRMED");
        assertThat(confirmed).as("at least one request must win").isGreaterThanOrEqualTo(1);
        assertThat(outcomes).filteredOn(o -> "REJECTED".equals(o.bookingStatus()))
                .extracting(Outcome::reason).allMatch(r -> r.startsWith("Seats already booked: "));

        List<Integer> sold = bookingSeatRepository.findAllConfirmedSeatNumbers(eventId);
        assertThat(sold).as("a seat sold twice would appear twice").doesNotHaveDuplicates();
        assertThat(sold).as("seats held by confirmed bookings").hasSize((int) (confirmed * 3));
        assertThat(bookingRepository.sumConfirmedSeats(eventId)).isEqualTo(confirmed * 3);
    }

    @Test
    void oneCustomerCannotBeatTheSixSeatLimitByRacing() throws Exception {
        long eventId = createEvent(organiser(), 30);
        Account customer = customer();
        String base = "limit-" + UUID.randomUUID() + "@example.com";

        // six requests of 2 DIFFERENT seats each (12 seats wanted), same customer, mixed letter case
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String email = (i % 2 == 0) ? base : base.toUpperCase();
            int first = 2 * i + 1;
            tasks.add(() -> attempt(customer, eventId, email, first, first + 1));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertEveryAttemptWasAnswered(outcomes);
        assertThat(count(outcomes, "CONFIRMED")).as("3 bookings x 2 seats = the limit").isEqualTo(3);
        assertThat(count(outcomes, "REJECTED")).isEqualTo(3);
        assertThat(outcomes).filteredOn(o -> "REJECTED".equals(o.bookingStatus()))
                .extracting(Outcome::reason).containsOnly(LIMIT_REASON);
        assertThat(bookingRepository.countConfirmedSeatsForCustomer(eventId, base)).isEqualTo(6L);
    }

    @Test
    void bookingsOnDifferentEvents_doNotBlockEachOther() throws Exception {
        Account organiser = organiser();
        long eventA = createEvent(organiser, 20);
        long eventB = createEvent(organiser, 20);
        Account customer = customer();

        // distinct seats everywhere, so every single request must succeed; a deadlock would time out
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int seat = 1; seat <= 5; seat++) {
            String emailA = uniqueEmail();
            String emailB = uniqueEmail();
            int s = seat;
            tasks.add(() -> attempt(customer, eventA, emailA, s));
            tasks.add(() -> attempt(customer, eventB, emailB, s));
        }
        List<Outcome> outcomes = runConcurrently(tasks);

        assertEveryAttemptWasAnswered(outcomes);
        assertThat(count(outcomes, "CONFIRMED")).isEqualTo(10);
        assertThat(bookingSeatRepository.findAllConfirmedSeatNumbers(eventA)).containsExactly(1, 2, 3, 4, 5);
        assertThat(bookingSeatRepository.findAllConfirmedSeatNumbers(eventB)).containsExactly(1, 2, 3, 4, 5);
    }

    // ------------------------------------------------------------------ the two layers, proven separately

    @Test
    void aBookingWaitsWhileAnotherTransactionHoldsTheEventLock() throws Exception {
        long eventId = createEvent(organiser(), 10);
        Account customer = customer();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch lockHeld = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> tx.executeWithoutResult(status -> {
                eventRepository.findByIdForUpdate(eventId);          // SELECT ... FOR UPDATE on the event row
                lockHeld.countDown();
                try {
                    Thread.sleep(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(lockHeld.await(10, TimeUnit.SECONDS)).isTrue();

            long started = System.nanoTime();
            Outcome outcome = attempt(customer, eventId, uniqueEmail(), 1);
            long waitedMs = (System.nanoTime() - started) / 1_000_000;
            holder.get(10, TimeUnit.SECONDS);

            assertThat(outcome.bookingStatus()).isEqualTo("CONFIRMED");
            assertThat(waitedMs).as("the booking must queue behind the lock holder, not run alongside it")
                    .isGreaterThanOrEqualTo(1000);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void partialUniqueIndex_stopsADoubleBooking_evenWhenTheLockIsBypassed() throws Exception {
        long eventId = createEvent(organiser(), 10);

        // straight to the repository: no event lock, no seat check. Only the database index is left.
        List<Callable<Throwable>> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String email = uniqueEmail();
            tasks.add(() -> {
                Booking booking = new Booking();
                booking.setEventId(eventId);
                booking.setCustomerName("Racer Test");
                booking.setCustomerEmail(email);
                booking.setSeats(1);
                booking.setStatus("CONFIRMED");
                booking.setBookedOn(LocalDate.now());
                booking.addSeat(1);
                try {
                    bookingRepository.save(booking);
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });
        }
        List<Throwable> results = runConcurrently(tasks);

        assertThat(results.stream().filter(t -> t == null).count()).as("inserts that got through").isEqualTo(1);
        assertThat(results).filteredOn(t -> t != null).hasSize(7)
                .allSatisfy(t -> assertThat(t).isInstanceOf(DataIntegrityViolationException.class));
        assertThat(bookingSeatRepository.findAllConfirmedSeatNumbers(eventId)).containsExactly(1);
    }

    // ------------------------------------------------------------------ helpers

    /** Starts every task on its own thread, releases them together, and waits for all of them. */
    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).as("all threads started").isTrue();
            go.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(40, TimeUnit.SECONDS));     // a deadlock shows up as a timeout
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private Outcome attempt(Account who, long eventId, String email, int... seats) throws Exception {
        MvcResult result = book(who, eventId, "John Doe", email, seats).andReturn();
        int http = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString();
        if (http != 201) {
            return new Outcome(http, null, body);
        }
        return new Outcome(http, readOrNull(body, "$.status"), readOrNull(body, "$.rejection_reason"));
    }

    private static String readOrNull(String json, String path) {
        try {
            return JsonPath.read(json, path);
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    private static long count(List<Outcome> outcomes, String bookingStatus) {
        return outcomes.stream().filter(o -> bookingStatus.equals(o.bookingStatus())).count();
    }

    /** The lock must turn every race into a normal answer; a 409 or 500 would mean the database caught it instead. */
    private static void assertEveryAttemptWasAnswered(List<Outcome> outcomes) {
        assertThat(outcomes)
                .as("every attempt must be answered 201 (CONFIRMED or REJECTED), never an error")
                .allSatisfy(o -> assertThat(o.httpStatus()).isEqualTo(201));
    }
}
