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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
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
  void bookingLists_areNewestFirst_andCanBeFilteredByStatus() {
    Event event = newEvent(50);
    Booking first = saveBooking(event, "a@x.com", "CONFIRMED", "40.00", 1);
    Booking second = saveBooking(event, "b@x.com", "REJECTED", null, 1);

    assertThat(bookingRepository.findByEventIdOrderByBookedOnDescIdDesc(event.getId()))
        .extracting(Booking::getId)
        .containsExactly(second.getId(), first.getId());
    assertThat(
            bookingRepository.findByEventIdAndStatusOrderByBookedOnDescIdDesc(
                event.getId(), "CONFIRMED"))
        .extracting(Booking::getId)
        .containsExactly(first.getId());
    // seats come with the booking (entity graph), no lazy-loading error outside a transaction
    assertThat(
            bookingRepository
                .findByEventIdOrderByBookedOnDescIdDesc(event.getId())
                .get(0)
                .getBookingSeats())
        .hasSize(1);
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
