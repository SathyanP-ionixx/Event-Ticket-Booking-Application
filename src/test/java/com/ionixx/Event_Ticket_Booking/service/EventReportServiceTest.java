package com.ionixx.Event_Ticket_Booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ionixx.Event_Ticket_Booking.dto.BookingResponse;
import com.ionixx.Event_Ticket_Booking.dto.EventSummaryResponse;
import com.ionixx.Event_Ticket_Booking.dto.SeatMapResponse;
import com.ionixx.Event_Ticket_Booking.entity.Booking;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.DemandBand;
import com.ionixx.Event_Ticket_Booking.enums.Role;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingValidationException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventNotFoundException;
import com.ionixx.Event_Ticket_Booking.repository.BookingRepository;
import com.ionixx.Event_Ticket_Booking.repository.BookingSeatRepository;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import com.ionixx.Event_Ticket_Booking.security.AuthUser;
import com.ionixx.Event_Ticket_Booking.support.TestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;

class EventReportServiceTest {

  private static final long EVENT_ID = 1L;
  private static final AuthUser OWNER = new AuthUser(2L, "o@test.com", Role.ORGANISER);
  private static final AuthUser OTHER = new AuthUser(3L, "x@test.com", Role.ORGANISER);

  private final EventRepository eventRepository = mock(EventRepository.class);
  private final BookingRepository bookingRepository = mock(BookingRepository.class);
  private final BookingSeatRepository seatRepository = mock(BookingSeatRepository.class);
  private final EventReportService service =
      new EventReportService(eventRepository, bookingRepository, seatRepository);

  private Event givenEvent(int totalSeats, Long organiserId) {
    Event e =
        TestData.event(EVENT_ID, totalSeats, LocalDate.now().plusDays(10), "100.00", organiserId);
    when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(e));
    return e;
  }

  // ------------------------------------------------------------------ listBookings

  @Test
  void listBookings_withoutStatus_returnsAll() {
    givenEvent(10, 2L);
    when(bookingRepository.findByEventIdOrderByBookedOnDescIdDesc(EVENT_ID))
        .thenReturn(
            List.of(
                TestData.booking(1L, EVENT_ID, 5L, "CONFIRMED", 2),
                TestData.booking(2L, EVENT_ID, 6L, "REJECTED", 1)));

    List<BookingResponse> result = service.listBookings(EVENT_ID, null, OWNER);

    assertThat(result)
        .extracting(BookingResponse::getStatus)
        .containsExactly("CONFIRMED", "REJECTED");
  }

  @Test
  void listBookings_withStatus_usesTheFilteredQuery() {
    givenEvent(10, 2L);
    Booking rejected = TestData.booking(2L, EVENT_ID, 6L, "REJECTED", 1);
    when(bookingRepository.findByEventIdAndStatusOrderByBookedOnDescIdDesc(EVENT_ID, "REJECTED"))
        .thenReturn(List.of(rejected));

    assertThat(service.listBookings(EVENT_ID, "REJECTED", OWNER)).hasSize(1);
    assertThat(service.listBookings(EVENT_ID, "REJECTED", OWNER).get(0).getStatus())
        .isEqualTo("REJECTED");
  }

  @Test
  void listBookings_acceptsConfirmed() {
    givenEvent(10, 2L);
    when(bookingRepository.findByEventIdAndStatusOrderByBookedOnDescIdDesc(EVENT_ID, "CONFIRMED"))
        .thenReturn(List.of());

    assertThat(service.listBookings(EVENT_ID, "CONFIRMED", OWNER)).isEmpty();
  }

  @Test
  void listBookings_invalidStatus_isRejectedBeforeAnyLookup() {
    assertThatThrownBy(() -> service.listBookings(EVENT_ID, "confirmed", OWNER))
        .isInstanceOf(BookingValidationException.class);
    verifyNoInteractions(eventRepository, bookingRepository);
  }

  @Test
  void listBookings_unknownEvent_isNotFound() {
    when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.listBookings(EVENT_ID, null, OWNER))
        .isInstanceOf(EventNotFoundException.class);
  }

  @Test
  void listBookings_anotherOrganiser_isForbidden() {
    givenEvent(10, 2L);

    assertThatThrownBy(() -> service.listBookings(EVENT_ID, null, OTHER))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void listBookings_eventWithoutOwner_isForbiddenToEveryone() {
    givenEvent(10, null);

    assertThatThrownBy(() -> service.listBookings(EVENT_ID, null, OWNER))
        .isInstanceOf(AccessDeniedException.class);
  }

  // ------------------------------------------------------------------ seat map

  @Test
  void seatMap_splitsBookedAndAvailable() {
    givenEvent(5, 2L);
    when(seatRepository.findAllConfirmedSeatNumbers(EVENT_ID)).thenReturn(List.of(2, 4));

    SeatMapResponse map = service.getSeatMap(EVENT_ID);

    assertThat(map.eventId()).isEqualTo(EVENT_ID);
    assertThat(map.totalSeats()).isEqualTo(5);
    assertThat(map.bookedSeats()).containsExactly(2, 4);
    assertThat(map.availableSeats()).containsExactly(1, 3, 5);
  }

  @Test
  void seatMap_unknownEvent_isNotFound() {
    when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSeatMap(EVENT_ID))
        .isInstanceOf(EventNotFoundException.class);
  }

  // ------------------------------------------------------------------ summary

  private void givenTotals(long seatsSold, long confirmed, long rejected, BigDecimal revenue) {
    when(bookingRepository.sumConfirmedSeats(EVENT_ID)).thenReturn(seatsSold);
    when(bookingRepository.countByEventIdAndStatus(EVENT_ID, "CONFIRMED")).thenReturn(confirmed);
    when(bookingRepository.countByEventIdAndStatus(EVENT_ID, "REJECTED")).thenReturn(rejected);
    when(bookingRepository.sumConfirmedRevenue(EVENT_ID)).thenReturn(revenue);
  }

  @Test
  void summary_computesEveryField() {
    givenEvent(200, 2L);
    givenTotals(100, 20, 3, new BigDecimal("1234.5"));

    EventSummaryResponse s = service.getSummary(EVENT_ID, OWNER);

    assertThat(s.eventId()).isEqualTo(EVENT_ID);
    assertThat(s.eventName()).isEqualTo("Event 1");
    assertThat(s.totalSeats()).isEqualTo(200);
    assertThat(s.seatsSold()).isEqualTo(100);
    assertThat(s.seatsAvailable()).isEqualTo(100);
    assertThat(s.confirmedBookings()).isEqualTo(20);
    assertThat(s.rejectedBookings()).isEqualTo(3);
    assertThat(s.totalRevenue()).isEqualByComparingTo("1234.50");
    assertThat(s.occupancyPct()).isEqualByComparingTo("50.0");
    assertThat(s.demandBand()).isEqualTo(DemandBand.MEDIUM);
  }

  @ParameterizedTest
  @CsvSource({"79,39.5,LOW", "80,40.0,MEDIUM", "150,75.0,MEDIUM", "151,75.5,HIGH"})
  void summary_demandBandFollowsTheRoundedOccupancy(long sold, String occupancy, DemandBand band) {
    givenEvent(200, 2L);
    givenTotals(sold, 1, 0, BigDecimal.TEN);

    EventSummaryResponse s = service.getSummary(EVENT_ID, OWNER);

    assertThat(s.occupancyPct()).isEqualByComparingTo(occupancy);
    assertThat(s.demandBand()).isEqualTo(band);
  }

  @Test
  void summary_noConfirmedBookings_hasZeroRevenue() {
    givenEvent(10, 2L);
    givenTotals(0, 0, 0, null);

    EventSummaryResponse s = service.getSummary(EVENT_ID, OWNER);

    assertThat(s.totalRevenue()).isEqualByComparingTo("0.00");
    assertThat(s.totalRevenue().scale()).isEqualTo(2);
    assertThat(s.demandBand()).isEqualTo(DemandBand.LOW);
  }

  @Test
  void summary_revenueRoundsHalfUp() {
    givenEvent(10, 2L);
    givenTotals(1, 1, 0, new BigDecimal("100.005"));

    assertThat(service.getSummary(EVENT_ID, OWNER).totalRevenue()).isEqualByComparingTo("100.01");
  }

  @Test
  void summary_eventWithZeroSeats_doesNotDivideByZero() {
    givenEvent(0, 2L);
    givenTotals(0, 0, 0, null);

    EventSummaryResponse s = service.getSummary(EVENT_ID, OWNER);

    assertThat(s.occupancyPct()).isEqualByComparingTo("0.0");
    assertThat(s.demandBand()).isEqualTo(DemandBand.LOW);
  }

  @Test
  void summary_anotherOrganiser_isForbidden_andNothingIsAggregated() {
    givenEvent(10, 2L);

    assertThatThrownBy(() -> service.getSummary(EVENT_ID, OTHER))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(bookingRepository);
  }

  @Test
  void summary_eventWithoutOwner_isForbidden() {
    givenEvent(10, null);

    assertThatThrownBy(() -> service.getSummary(EVENT_ID, OWNER))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void summary_unknownEvent_isNotFound() {
    when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSummary(EVENT_ID, OWNER))
        .isInstanceOf(EventNotFoundException.class);
    verify(eventRepository).findById(EVENT_ID);
  }

  // ------------------------------------------------------------------ band edges

  @Test
  void bandFor_edges() {
    assertThat(EventReportService.bandFor(new BigDecimal("39.9"))).isEqualTo(DemandBand.LOW);
    assertThat(EventReportService.bandFor(new BigDecimal("40"))).isEqualTo(DemandBand.MEDIUM);
    assertThat(EventReportService.bandFor(new BigDecimal("75"))).isEqualTo(DemandBand.MEDIUM);
    assertThat(EventReportService.bandFor(new BigDecimal("75.1"))).isEqualTo(DemandBand.HIGH);
  }
}
