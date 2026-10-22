package com.ionixx.Event_Ticket_Booking.service;

import com.ionixx.Event_Ticket_Booking.dto.BookingRequest;
import com.ionixx.Event_Ticket_Booking.dto.BookingResponse;
import com.ionixx.Event_Ticket_Booking.entity.Booking;
import com.ionixx.Event_Ticket_Booking.entity.BookingSeat;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingNotFoundException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingValidationException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventNotFoundException;
import com.ionixx.Event_Ticket_Booking.repository.BookingRepository;
import com.ionixx.Event_Ticket_Booking.repository.BookingSeatRepository;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import com.ionixx.Event_Ticket_Booking.validators.BookingValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final String EMAIL = "priya@example.com";
    private static final String MAX_REASON = "Customer already holds the maximum seats for this event";

    @Mock BookingRepository bookingRepository;
    @Mock BookingSeatRepository bookingSeatRepository;
    @Mock EventRepository eventRepository;
    @Mock BookingValidator validator;
    @Captor ArgumentCaptor<Booking> bookingCaptor;
    @Captor ArgumentCaptor<Collection<Integer>> seatsCaptor;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, bookingSeatRepository, eventRepository,
                validator, new PriceCalculator());
        // "save" returns what it was given (id stays null; irrelevant for these tests)
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- helpers ----------

    private Event stubEvent(int totalSeats, String basePrice, LocalDate date) {
        Event e = new Event();
        ReflectionTestUtils.setField(e, "id", 1L);
        e.setName("Chennai Jazz Night");
        e.setTotalSeats(totalSeats);
        e.setBasePrice(new BigDecimal(basePrice));
        e.setEventDate(date);
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(e));
        return e;
    }

    private BookingRequest request(String email, Integer... seats) {
        BookingRequest r = new BookingRequest();
        r.setEventId(1L);
        r.setCustomerName("Priya Raman");
        r.setCustomerEmail(email);
        r.setSeatNumbers(Arrays.asList(seats));
        return r;
    }

    private Booking savedBooking() {
        verify(bookingRepository).save(bookingCaptor.capture());
        return bookingCaptor.getValue();
    }

    // ---------- step 1/2: validation and lookups ----------

    @Test
    void validationFailure_propagates_andNothingElseIsTouched() {
        BookingRequest req = request(EMAIL, 1);
        doThrow(new BookingValidationException("seat_numbers", "bad")).when(validator).validate(req);

        assertThrows(BookingValidationException.class, () -> service.createBooking(req));

        verifyNoInteractions(eventRepository, bookingSeatRepository);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void unknownEvent_throwsEventNotFound_withSpecMessage_andSavesNothing() {
        when(eventRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        EventNotFoundException ex = assertThrows(EventNotFoundException.class,
                () -> service.createBooking(request(EMAIL, 1)));

        assertEquals("Event with id 1 not found", ex.getMessage());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void seatAboveTotalSeats_is400_namingTheSmallestOffendingSeat() {
        stubEvent(20, "1000", LocalDate.now().plusDays(10));

        BookingValidationException ex = assertThrows(BookingValidationException.class,
                () -> service.createBooking(request(EMAIL, 25, 21, 3)));

        assertEquals(1, ex.getDetails().size());
        assertEquals("seat_numbers", ex.getDetails().get(0).field());
        assertEquals("seat 21 does not exist for this event", ex.getDetails().get(0).message());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void seatEqualToTotalSeats_isAllowed() {
        stubEvent(20, "1000", LocalDate.now().plusDays(10));

        BookingResponse r = service.createBooking(request(EMAIL, 20));

        assertEquals("CONFIRMED", r.getStatus());
    }

    // ---------- step 3: rules, first failure decides ----------

    @Test
    void pastEvent_isRejected_withDateReason_andRuleChecksStop() {
        stubEvent(200, "1500", LocalDate.now().minusDays(1));

        BookingResponse r = service.createBooking(request(EMAIL, 12, 13));

        assertEquals("REJECTED", r.getStatus());
        assertEquals("Event is already over", r.getRejectionReason());
        assertNull(r.getPricePerSeat());
        assertNull(r.getTotalAmount());
        assertEquals(2, r.getSeats());
        // later rules never ran
        verifyNoInteractions(bookingSeatRepository);
        verify(bookingRepository, never()).countConfirmedSeatsForCustomer(any(), any());
    }

    @Test
    void rejectedBooking_isSaved_withItsSeatRows_markedRejected() {
        stubEvent(200, "1500", LocalDate.now().minusDays(1));

        service.createBooking(request(EMAIL, 13, 12));

        Booking saved = savedBooking();
        assertEquals("REJECTED", saved.getStatus());
        assertEquals(2, saved.getBookingSeats().size());
        assertTrue(saved.getBookingSeats().stream().allMatch(s -> "REJECTED".equals(s.getStatus())));
    }

    @Test
    void eventToday_isStillBookable() {
        stubEvent(200, "1500", LocalDate.now());

        BookingResponse r = service.createBooking(request(EMAIL, 1));

        assertEquals("CONFIRMED", r.getStatus());
        assertEquals(new BigDecimal("1500.00"), r.getPricePerSeat());   // no discounts today
    }

    @Test
    void takenSeats_areListedInAscendingOrder_andCustomerRuleIsSkipped() {
        stubEvent(200, "1500", LocalDate.now().plusDays(10));
        when(bookingSeatRepository.findConfirmedSeatNumbers(eq(1L), anyCollection())).thenReturn(List.of(12, 14));

        BookingResponse r = service.createBooking(request(EMAIL, 16, 14, 12));

        assertEquals("REJECTED", r.getStatus());
        assertEquals("Seats already booked: 12, 14", r.getRejectionReason());
        assertNull(r.getPricePerSeat());
        assertNull(r.getTotalAmount());
        verify(bookingRepository, never()).countConfirmedSeatsForCustomer(any(), any());
    }

    @Test
    void availabilityQuery_receivesTheSeatsSorted() {
        stubEvent(200, "1500", LocalDate.now().plusDays(10));

        service.createBooking(request(EMAIL, 16, 12, 14));

        verify(bookingSeatRepository).findConfirmedSeatNumbers(eq(1L), seatsCaptor.capture());
        assertEquals(List.of(12, 14, 16), List.copyOf(seatsCaptor.getValue()));
    }

    @ParameterizedTest(name = "already holds {0}, asks {1} -> confirmed={2}")
    @CsvSource({
            "0, 6, true",
            "4, 2, true",    // exactly 6 in total
            "5, 1, true",
            "4, 3, false",   // 7 in total
            "6, 1, false",
            "3, 4, false"
    })
    void perCustomerLimit(long held, int asks, boolean confirmed) {
        stubEvent(200, "1000", LocalDate.now().plusDays(10));
        when(bookingRepository.countConfirmedSeatsForCustomer(1L, EMAIL)).thenReturn(held);
        Integer[] seats = IntStream.rangeClosed(1, asks).boxed().toArray(Integer[]::new);

        BookingResponse r = service.createBooking(request(EMAIL, seats));

        if (confirmed) {
            assertEquals("CONFIRMED", r.getStatus());
            assertNull(r.getRejectionReason());
        } else {
            assertEquals("REJECTED", r.getStatus());
            assertEquals(MAX_REASON, r.getRejectionReason());
        }
    }

    // ---------- steps 4-6: pricing and saving ----------

    @Test
    void confirmedBooking_workedExample_1282_50_and_5130_00() {
        Event event = stubEvent(200, "1500", LocalDate.now().plusDays(71));

        BookingResponse r = service.createBooking(request(EMAIL, 15, 12, 14, 13));

        assertEquals("CONFIRMED", r.getStatus());
        assertNull(r.getRejectionReason());
        assertEquals(new BigDecimal("1282.50"), r.getPricePerSeat());
        assertEquals(new BigDecimal("5130.00"), r.getTotalAmount());
        assertEquals(4, r.getSeats());
        assertEquals(List.of(12, 13, 14, 15), r.getSeatNumbers());
        assertEquals(LocalDate.now(), r.getBookedOn());
        assertEquals(event.getId(), r.getEventId());
        assertEquals("Priya Raman", r.getCustomerName());
        assertEquals(EMAIL, r.getCustomerEmail());
    }

    @Test
    void confirmedBooking_noDiscounts_1000x2() {
        stubEvent(200, "1000", LocalDate.now().plusDays(10));

        BookingResponse r = service.createBooking(request(EMAIL, 1, 2));

        assertEquals(new BigDecimal("1000.00"), r.getPricePerSeat());
        assertEquals(new BigDecimal("2000.00"), r.getTotalAmount());
    }

    @Test
    void confirmedBooking_savesOneSeatRowPerSeat_markedConfirmed() {
        stubEvent(200, "1000", LocalDate.now().plusDays(10));

        service.createBooking(request(EMAIL, 3, 1, 2));

        Booking saved = savedBooking();
        assertEquals("CONFIRMED", saved.getStatus());
        assertEquals(3, saved.getSeats());
        assertEquals(List.of(1, 2, 3), saved.getBookingSeats().stream().map(BookingSeat::getSeatNumber).toList());
        assertTrue(saved.getBookingSeats().stream().allMatch(s -> "CONFIRMED".equals(s.getStatus())));
        assertTrue(saved.getBookingSeats().stream().allMatch(s -> Long.valueOf(1L).equals(s.getEventId())));
    }

    // ---------- API 4 ----------

    @Test
    void getBooking_notFound_hasSpecMessage() {
        when(bookingRepository.findById(42L)).thenReturn(Optional.empty());

        BookingNotFoundException ex = assertThrows(BookingNotFoundException.class, () -> service.getBooking(42L));

        assertEquals("Booking with id 42 not found", ex.getMessage());
    }

    @Test
    void getBooking_found_returnsSortedSeatNumbers() {
        Booking b = new Booking();
        b.setEventId(1L);
        b.setCustomerName("Priya Raman");
        b.setCustomerEmail(EMAIL);
        b.setSeats(2);
        b.setStatus("CONFIRMED");
        b.setPricePerSeat(new BigDecimal("1000.00"));
        b.setTotalAmount(new BigDecimal("2000.00"));
        b.setBookedOn(LocalDate.now());
        b.addSeat(15);
        b.addSeat(12);
        when(bookingRepository.findById(5L)).thenReturn(Optional.of(b));

        BookingResponse r = service.getBooking(5L);

        assertEquals(List.of(12, 15), r.getSeatNumbers());
        assertEquals("CONFIRMED", r.getStatus());
        assertEquals(new BigDecimal("2000.00"), r.getTotalAmount());
    }
}
