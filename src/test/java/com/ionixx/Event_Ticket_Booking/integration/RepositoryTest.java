package com.ionixx.Event_Ticket_Booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ionixx.Event_Ticket_Booking.entity.Booking;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.entity.User;
import com.ionixx.Event_Ticket_Booking.enums.Category;
import com.ionixx.Event_Ticket_Booking.enums.Role;
import com.ionixx.Event_Ticket_Booking.repository.BookingRepository;
import com.ionixx.Event_Ticket_Booking.repository.BookingSeatRepository;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import com.ionixx.Event_Ticket_Booking.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Queries and constraints that only a real PostgreSQL can prove (partial unique index, locking,
 * lower()).
 */
class RepositoryTest extends AbstractIntegrationTest {

  @Autowired EventRepository eventRepository;
  @Autowired BookingRepository bookingRepository;
  @Autowired BookingSeatRepository bookingSeatRepository;
  @Autowired UserRepository userRepository;
  @Autowired PlatformTransactionManager transactionManager;
  @Autowired EntityManagerFactory entityManagerFactory;

  private Event newEvent(int seats) {
    Event e = new Event();
    e.setName(uniqueEventName());
    e.setCategory(Category.THEATRE);
    e.setBasePrice(new BigDecimal("40.00"));
    e.setTotalSeats(seats);
    e.setEventDate(LocalDate.now().plusDays(30));
    e.setVenue("Repo Hall");
    return eventRepository.save(e);
  }

  private Booking saveBooking(
      Event event, String email, String status, String amount, int... seatNumbers) {
    Booking b = new Booking();
    b.setEventId(event.getId());
    b.setCustomerName("Repo Tester");
    b.setCustomerEmail(email);
    b.setSeats(seatNumbers.length);
    b.setStatus(status);
    b.setBookedOn(LocalDate.now());
    if (amount != null) {
      b.setPricePerSeat(new BigDecimal(amount));
      b.setTotalAmount(new BigDecimal(amount).multiply(BigDecimal.valueOf(seatNumbers.length)));
    }
    for (int s : seatNumbers) {
      b.addSeat(s);
    }
    return bookingRepository.save(b);
  }

  // ------------------------------------------------------------------ seats

  @Test
  void confirmedSeatQueries_ignoreRejectedBookings_andAreSorted() {
    Event event = newEvent(20);
    saveBooking(event, "a@x.com", "CONFIRMED", "40.00", 9, 3);
    saveBooking(event, "b@x.com", "REJECTED", null, 3, 4); // same seat 3, but rejected: allowed

    assertThat(bookingSeatRepository.findAllConfirmedSeatNumbers(event.getId()))
        .containsExactly(3, 9);
    assertThat(bookingSeatRepository.findConfirmedSeatNumbers(event.getId(), List.of(3, 4, 9)))
        .containsExactly(3, 9);
    assertThat(bookingSeatRepository.findConfirmedSeatNumbers(event.getId(), List.of(4))).isEmpty();
  }

  @Test
  void partialUniqueIndex_blocksADoubleBooking_atTheDatabaseLevel() {
    Event event = newEvent(20);
    saveBooking(event, "a@x.com", "CONFIRMED", "40.00", 5);

    assertThatThrownBy(() -> saveBooking(event, "b@x.com", "CONFIRMED", "40.00", 5))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  // ------------------------------------------------------------------ aggregates

  @Test
  void aggregates_countOnlyConfirmedBookings() {
    Event event = newEvent(50);
    saveBooking(event, "a@x.com", "CONFIRMED", "40.00", 1, 2);
    saveBooking(event, "b@x.com", "CONFIRMED", "10.50", 3);
    saveBooking(event, "c@x.com", "REJECTED", null, 1);

    assertThat(bookingRepository.sumConfirmedSeats(event.getId())).isEqualTo(3L);
    assertThat(bookingRepository.sumConfirmedRevenue(event.getId())).isEqualByComparingTo("90.50");
    assertThat(bookingRepository.countByEventIdAndStatus(event.getId(), "CONFIRMED")).isEqualTo(2L);
    assertThat(bookingRepository.countByEventIdAndStatus(event.getId(), "REJECTED")).isEqualTo(1L);
  }

  @Test
  void aggregates_onAnEmptyEvent() {
    Event event = newEvent(50);

    assertThat(bookingRepository.sumConfirmedSeats(event.getId())).isZero();
    assertThat(bookingRepository.sumConfirmedRevenue(event.getId())).isNull();
  }

  @Test
  void customerSeatCount_isCaseInsensitive_andPerEvent() {
    Event event = newEvent(50);
    Event other = newEvent(50);
    saveBooking(event, "John@Example.com", "CONFIRMED", "40.00", 1, 2);
    saveBooking(event, "john@example.com", "REJECTED", null, 3);
    saveBooking(other, "john@example.com", "CONFIRMED", "40.00", 1);

    assertThat(bookingRepository.countConfirmedSeatsForCustomer(event.getId(), "JOHN@example.COM"))
        .isEqualTo(2L);
    assertThat(
            bookingRepository.countConfirmedSeatsForCustomer(event.getId(), "nobody@example.com"))
        .isZero();
  }

  @Test
  void bookingPages_areNewestFirst_stable_andFilterable() {
    Event event = newEvent(50);
    List<Long> ids = new ArrayList<>(); // oldest first
    for (int i = 1; i <= 5; i++) {
      boolean rejected = i == 2;
      ids.add(
          saveBooking(
                  event,
                  "p" + i + "@x.com",
                  rejected ? "REJECTED" : "CONFIRMED",
                  rejected ? null : "40.00",
                  i)
              .getId());
    }
    // same booked_on for all: the order falls back to id descending -> 5, 4, 3, 2, 1

    Page<Booking> first =
        bookingRepository.findByEventIdOrderByBookedOnDescIdDesc(
            event.getId(), PageRequest.of(0, 2));
    Page<Booking> second =
        bookingRepository.findByEventIdOrderByBookedOnDescIdDesc(
            event.getId(), PageRequest.of(1, 2));
    Page<Booking> third =
        bookingRepository.findByEventIdOrderByBookedOnDescIdDesc(
            event.getId(), PageRequest.of(2, 2));
    Page<Booking> beyond =
        bookingRepository.findByEventIdOrderByBookedOnDescIdDesc(
            event.getId(), PageRequest.of(3, 2));

    assertThat(first.getContent())
        .extracting(Booking::getId)
        .containsExactly(ids.get(4), ids.get(3));
    assertThat(second.getContent())
        .extracting(Booking::getId)
        .containsExactly(ids.get(2), ids.get(1));
    assertThat(third.getContent()).extracting(Booking::getId).containsExactly(ids.get(0));
    assertThat(beyond.getContent()).isEmpty();
    assertThat(first.getTotalElements()).isEqualTo(5);
    assertThat(first.getTotalPages()).isEqualTo(3);
    assertThat(beyond.getTotalElements()).as("totals stay correct past the last page").isEqualTo(5);

    Page<Booking> confirmed =
        bookingRepository.findByEventIdAndStatusOrderByBookedOnDescIdDesc(
            event.getId(), "CONFIRMED", PageRequest.of(0, 3));
    Page<Booking> rejectedOnly =
        bookingRepository.findByEventIdAndStatusOrderByBookedOnDescIdDesc(
            event.getId(), "REJECTED", PageRequest.of(0, 3));

    assertThat(confirmed.getContent())
        .extracting(Booking::getId)
        .containsExactly(ids.get(4), ids.get(3), ids.get(2));
    assertThat(confirmed.getTotalElements()).isEqualTo(4);
    assertThat(confirmed.getTotalPages()).isEqualTo(2);
    assertThat(rejectedOnly.getContent()).extracting(Booking::getId).containsExactly(ids.get(1));
  }

  @Test
  void bookingPages_onlyContainTheRequestedEvent() {
    Event event = newEvent(50);
    Event other = newEvent(50);
    saveBooking(event, "a@x.com", "CONFIRMED", "40.00", 1);
    saveBooking(other, "b@x.com", "CONFIRMED", "40.00", 1);

    Page<Booking> page =
        bookingRepository.findByEventIdOrderByBookedOnDescIdDesc(
            event.getId(), PageRequest.of(0, 10));

    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent()).extracting(Booking::getEventId).containsOnly(event.getId());
  }

  @Test
  void bookingSeats_areLoadedInBatches_notOneQueryPerBooking() {
    Event event = newEvent(30);
    for (int i = 1; i <= 12; i++) {
      saveBooking(event, "b" + i + "@x.com", "CONFIRMED", "40.00", i);
    }
    Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    assertThat(stats.isStatisticsEnabled())
        .as("generate_statistics must be on for this test")
        .isTrue();
    TransactionTemplate tx = new TransactionTemplate(transactionManager);

    long before = stats.getPrepareStatementCount();
    Integer seatCount =
        tx.execute(
            status -> {
              Page<Booking> page =
                  bookingRepository.findByEventIdOrderByBookedOnDescIdDesc(
                      event.getId(), PageRequest.of(0, 12));
              return page.getContent().stream().mapToInt(b -> b.getBookingSeats().size()).sum();
            });
    long statements = stats.getPrepareStatementCount() - before;

    assertThat(seatCount).isEqualTo(12);
    // page + count + ONE batched seat load; a missing @BatchSize would need 12 seat loads (14
    // statements)
    assertThat(statements).isLessThanOrEqualTo(4);
  }

  // ------------------------------------------------------------------ events

  @Test
  void findByNameAndEventDate_matchesBoth() {
    Event event = newEvent(20);

    assertThat(eventRepository.findByNameAndEventDate(event.getName(), event.getEventDate()))
        .isPresent();
    assertThat(
            eventRepository.findByNameAndEventDate(
                event.getName(), event.getEventDate().plusDays(1)))
        .isEmpty();
  }

  @Test
  void nameAndDate_areUniqueInTheDatabase() {
    Event event = newEvent(20);
    Event duplicate = new Event();
    duplicate.setName(event.getName());
    duplicate.setCategory(Category.MUSIC);
    duplicate.setBasePrice(BigDecimal.TEN);
    duplicate.setTotalSeats(20);
    duplicate.setEventDate(event.getEventDate());
    duplicate.setVenue("Elsewhere");

    assertThatThrownBy(() -> eventRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void findByIdForUpdate_takesARowLockInsideATransaction() {
    Event event = newEvent(20);
    TransactionTemplate tx = new TransactionTemplate(transactionManager);

    Optional<Event> locked = tx.execute(status -> eventRepository.findByIdForUpdate(event.getId()));
    Optional<Event> missing = tx.execute(status -> eventRepository.findByIdForUpdate(MISSING_ID));

    assertThat(locked).isPresent();
    assertThat(missing).isEmpty();
  }

  // ------------------------------------------------------------------ users

  @Test
  void users_findByEmail_existsByEmail_andUniqueEmail() {
    String email = uniqueEmail();
    User user = new User();
    user.setEmail(email);
    user.setPassword("$2a$10$hash");
    user.setRole(Role.CUSTOMER);
    userRepository.save(user);

    assertThat(userRepository.existsByEmail(email)).isTrue();
    assertThat(userRepository.existsByEmail("nobody@test.com")).isFalse();
    assertThat(userRepository.findByEmail(email))
        .get()
        .extracting(User::getRole)
        .isEqualTo(Role.CUSTOMER);

    User clash = new User();
    clash.setEmail(email);
    clash.setPassword("x");
    clash.setRole(Role.ORGANISER);
    assertThatThrownBy(() -> userRepository.saveAndFlush(clash))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
