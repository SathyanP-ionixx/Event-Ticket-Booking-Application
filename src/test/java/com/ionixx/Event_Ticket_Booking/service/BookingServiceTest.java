package com.ionixx.Event_Ticket_Booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ionixx.Event_Ticket_Booking.dto.BookingRequest;
import com.ionixx.Event_Ticket_Booking.dto.BookingResponse;
import com.ionixx.Event_Ticket_Booking.entity.Booking;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.Role;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingNotFoundException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingValidationException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventNotFoundException;
import com.ionixx.Event_Ticket_Booking.repository.BookingRepository;
import com.ionixx.Event_Ticket_Booking.repository.BookingSeatRepository;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import com.ionixx.Event_Ticket_Booking.security.AuthUser;
import com.ionixx.Event_Ticket_Booking.support.TestData;
import com.ionixx.Event_Ticket_Booking.validators.BookingValidator;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class BookingServiceTest {

  private static final long EVENT_ID = 1L;
  private static final long USER_ID = 9L;
  private static final String EMAIL = "john@example.com";

  private final BookingRepository bookingRepository = mock(BookingRepository.class);
  private final BookingSeatRepository seatRepository = mock(BookingSeatRepository.class);
  private final EventRepository eventRepository = mock(EventRepository.class);
  private final BookingService service =
      new BookingService(
          bookingRepository,
          seatRepository,
          eventRepository,
          new BookingValidator(),
          new PriceCalculator());

  @BeforeEach
  void defaultStubs() {
    when(bookingRepository.save(any(Booking.class)))
        .thenAnswer(
            inv -> {
              Booking b = inv.getArgument(0);
              ReflectionTestUtils.setField(b, "id", 100L);
              return b;
            });
    when(seatRepository.findConfirmedSeatNumbers(anyLong(), anyCollection())).thenReturn(List.of());
    when(bookingRepository.countConfirmedSeatsForCustomer(anyLong(), anyString())).thenReturn(0L);
  }

  private void givenEvent(Event event) {
    when(eventRepository.findByIdForUpdate(EVENT_ID)).thenReturn(Optional.of(event));
  }

  private static Event futureEvent(int daysAhead) {
    return TestData.event(EVENT_ID, 100, LocalDate.now().plusDays(daysAhead), "100.00", 2L);
  }

  // ------------------------------------------------------------------ createBooking

  @Test
  void confirmedBooking_isPriced_sorted_andOwnedByTheCaller() {
    givenEvent(futureEvent(45));

    BookingResponse res =
        service.createBooking(
            TestData.bookingRequest(EVENT_ID, "John Doe", EMAIL, 4, 2, 1, 3), USER_ID);

    assertThat(res.getStatus()).isEqualTo("CONFIRMED");
    assertThat(res.getSeatNumbers()).containsExactly(1, 2, 3, 4);
    assertThat(res.getSeats()).isEqualTo(4);
    assertThat(res.getPricePerSeat()).isEqualByComparingTo("85.50"); // early bird + group
    assertThat(res.getTotalAmount()).isEqualByComparingTo("342.00");
    assertThat(res.getRejectionReason()).isNull();
    assertThat(res.getId()).isEqualTo(100L);

    ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
    verify(bookingRepository).save(saved.capture());
    assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
    assertThat(saved.getValue().getBookedOn()).isEqualTo(LocalDate.now());
  }

  @Test
  void eventToday_isStillBookable_withoutDiscounts() {
    givenEvent(futureEvent(0));

    BookingResponse res =
        service.createBooking(TestData.bookingRequest(EVENT_ID, "John Doe", EMAIL, 1), USER_ID);

    assertThat(res.getStatus()).isEqualTo("CONFIRMED");
    assertThat(res.getPricePerSeat()).isEqualByComparingTo("100.00");
  }

  @Test
  void eventInThePast_isRejectedAndStillSaved() {
    givenEvent(futureEvent(-1));

    BookingResponse res =
        service.createBooking(TestData.bookingRequest(EVENT_ID, "John Doe", EMAIL, 1), USER_ID);

    assertThat(res.getStatus()).isEqualTo("REJECTED");
    assertThat(res.getRejectionReason()).isEqualTo("Event is already over");
    assertThat(res.getPricePerSeat()).isNull();
    assertThat(res.getTotalAmount()).isNull();
    verify(bookingRepository).save(any(Booking.class));
  }

  @Test
  void seatsAlreadyTaken_areRejectedWithTheirNumbers() {
    givenEvent(futureEvent(10));
    doReturn(List.of(2, 3))
        .when(seatRepository)
        .findConfirmedSeatNumbers(anyLong(), anyCollection());

    BookingResponse res =
        service.createBooking(
            TestData.bookingRequest(EVENT_ID, "John Doe", EMAIL, 1, 2, 3), USER_ID);

    assertThat(res.getStatus()).isEqualTo("REJECTED");
    assertThat(res.getRejectionReason()).isEqualTo("Seats already booked: 2, 3");
  }

  @Test
  void customerOverTheSixSeatLimit_isRejected() {
    givenEvent(futureEvent(10));
    doReturn(4L).when(bookingRepository).countConfirmedSeatsForCustomer(anyLong(), anyString());

    BookingResponse res =
        service.createBooking(
            TestData.bookingRequest(EVENT_ID, "John Doe", EMAIL, 1, 2, 3), USER_ID);

    assertThat(res.getStatus()).isEqualTo("REJECTED");
    assertThat(res.getRejectionReason())
        .isEqualTo("Customer already holds the maximum seats for this event");
  }

  @Test
  void customerReachingExactlySixSeats_isConfirmed() {
    givenEvent(futureEvent(10));
    doReturn(4L).when(bookingRepository).countConfirmedSeatsForCustomer(anyLong(), anyString());

    BookingResponse res =
        service.createBooking(TestData.bookingRequest(EVENT_ID, "John Doe", EMAIL, 1, 2), USER_ID);

    assertThat(res.getStatus()).isEqualTo("CONFIRMED");
  }

  @Test
  void seatBeyondTheVenue_isAValidationError_andNothingIsSaved() {
    givenEvent(TestData.event(EVENT_ID, 10, LocalDate.now().plusDays(10), "100.00", 2L));

    assertThatThrownBy(
            () ->
                service.createBooking(
                    TestData.bookingRequest(EVENT_ID, "John Doe", EMAIL, 11), USER_ID))
        .isInstanceOfSatisfying(
            BookingValidationException.class,
            ex ->
                assertThat(ex.getDetails())
                    .singleElement()
                    .satisfies(
                        d -> {
                          assertThat(d.field()).isEqualTo("seat_numbers");
                          assertThat(d.message())
                              .isEqualTo("seat 11 does not exist for this event");
                        }));
    verify(bookingRepository, never()).save(any());
  }

  @Test
  void unknownEvent_isNotFound() {
    when(eventRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                service.createBooking(TestData.bookingRequest(99L, "John Doe", EMAIL, 1), USER_ID))
        .isInstanceOf(EventNotFoundException.class)
        .hasMessage("Event with id 99 not found");
  }

  @Test
  void invalidBody_failsBeforeTouchingTheDatabase() {
    BookingRequest bad = new BookingRequest();

    assertThatThrownBy(() -> service.createBooking(bad, USER_ID))
        .isInstanceOf(BookingValidationException.class);
    verifyNoInteractions(eventRepository);
  }

  // ------------------------------------------------------------------ getBooking

  private Booking existingBooking(Long ownerId) {
    Booking b = TestData.booking(5L, EVENT_ID, ownerId, "CONFIRMED", 2);
    when(bookingRepository.findById(5L)).thenReturn(Optional.of(b));
    return b;
  }

  private static AuthUser customer(long id) {
    return new AuthUser(id, "c@test.com", Role.CUSTOMER);
  }

  private static AuthUser organiser(long id) {
    return new AuthUser(id, "o@test.com", Role.ORGANISER);
  }

  @Test
  void owner_canReadTheirBooking() {
    existingBooking(USER_ID);

    assertThat(service.getBooking(5L, customer(USER_ID)).getEventId()).isEqualTo(EVENT_ID);
  }

  @Test
  void anotherCustomer_getsNotFound() {
    existingBooking(USER_ID);

    assertThatThrownBy(() -> service.getBooking(5L, customer(77L)))
        .isInstanceOf(BookingNotFoundException.class)
        .hasMessage("Booking not found with id 5");
  }

  @Test
  void organiserOfTheEvent_canReadAnyBookingOnIt() {
    existingBooking(USER_ID);
    when(eventRepository.findById(EVENT_ID))
        .thenReturn(
            Optional.of(TestData.event(EVENT_ID, 100, LocalDate.now().plusDays(5), "10.00", 2L)));

    assertThat(service.getBooking(5L, organiser(2L))).isNotNull();
  }

  @Test
  void organiserOfAnotherEvent_getsNotFound() {
    existingBooking(USER_ID);
    when(eventRepository.findById(EVENT_ID))
        .thenReturn(
            Optional.of(TestData.event(EVENT_ID, 100, LocalDate.now().plusDays(5), "10.00", 2L)));

    assertThatThrownBy(() -> service.getBooking(5L, organiser(3L)))
        .isInstanceOf(BookingNotFoundException.class);
  }

  @Test
  void organiser_whenTheEventNoLongerExists_getsNotFound() {
    existingBooking(USER_ID);
    when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getBooking(5L, organiser(2L)))
        .isInstanceOf(BookingNotFoundException.class);
  }

  @Test
  void organiserWhoOwnsTheBookingItself_canRead() {
    existingBooking(3L);

    assertThat(service.getBooking(5L, organiser(3L))).isNotNull();
  }

  @Test
  void legacyBookingWithoutOwner_isHiddenFromCustomers_butVisibleToTheEventOrganiser() {
    existingBooking(null);
    when(eventRepository.findById(EVENT_ID))
        .thenReturn(
            Optional.of(TestData.event(EVENT_ID, 100, LocalDate.now().plusDays(5), "10.00", 2L)));

    assertThatThrownBy(() -> service.getBooking(5L, customer(USER_ID)))
        .isInstanceOf(BookingNotFoundException.class);
    assertThat(service.getBooking(5L, organiser(2L))).isNotNull();
  }

  @Test
  void missingBooking_isNotFound() {
    when(bookingRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getBooking(404L, customer(1L)))
        .isInstanceOf(BookingNotFoundException.class)
        .hasMessage("Booking not found with id 404");
  }
}
