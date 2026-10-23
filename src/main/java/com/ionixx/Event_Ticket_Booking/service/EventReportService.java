package com.ionixx.Event_Ticket_Booking.service;

import com.ionixx.Event_Ticket_Booking.dto.BookingResponse;
import com.ionixx.Event_Ticket_Booking.dto.EventSummaryResponse;
import com.ionixx.Event_Ticket_Booking.dto.SeatMapResponse;
import com.ionixx.Event_Ticket_Booking.entity.Booking;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.DemandBand;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingValidationException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventNotFoundException;
import com.ionixx.Event_Ticket_Booking.repository.BookingRepository;
import com.ionixx.Event_Ticket_Booking.repository.BookingSeatRepository;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

@Service
public class EventReportService {

    private static final String CONFIRMED = "CONFIRMED";
    private static final String REJECTED = "REJECTED";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal LOW_BELOW = BigDecimal.valueOf(40);
    private static final BigDecimal HIGH_ABOVE = BigDecimal.valueOf(75);

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;

    public EventReportService(EventRepository eventRepository,
                              BookingRepository bookingRepository,
                              BookingSeatRepository bookingSeatRepository) {
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
    }

    //  List bookings for an event

    /** @param status null for all bookings, otherwise exactly CONFIRMED or REJECTED (else 400). */
    @Transactional(readOnly = true)
    public List<BookingResponse> listBookings(long eventId, String status) {
        if (status != null && !CONFIRMED.equals(status) && !REJECTED.equals(status)) {
            throw new BookingValidationException("status", "must be CONFIRMED or REJECTED");
        }
        requireEvent(eventId);

        List<Booking> bookings = (status == null)
                ? bookingRepository.findByEventIdOrderByBookedOnDescIdDesc(eventId)
                : bookingRepository.findByEventIdAndStatusOrderByBookedOnDescIdDesc(eventId, status);
        return bookings.stream().map(BookingResponse::from).toList();
    }

    // ---------- API 6 ----------

    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(long eventId) {
        Event event = requireEvent(eventId);

        List<Integer> booked = bookingSeatRepository.findAllConfirmedSeatNumbers(eventId); // ascending
        Set<Integer> bookedSet = new HashSet<>(booked);
        List<Integer> available = IntStream.rangeClosed(1, event.getTotalSeats())
                .filter(n -> !bookedSet.contains(n))
                .boxed()
                .toList();

        return new SeatMapResponse(event.getId(), event.getTotalSeats(), booked, available);
    }

    // ---------- API 7 ----------

    /**
     * Computed with SQL aggregates (count / sum) so the database does the adding and only three
     * numbers come back, instead of loading every booking into memory just to total them.
     */
    @Transactional(readOnly = true)
    public EventSummaryResponse getSummary(long eventId) {
        Event event = requireEvent(eventId);

        long seatsSold = bookingRepository.sumConfirmedSeats(eventId);
        long confirmed = bookingRepository.countByEventIdAndStatus(eventId, CONFIRMED);
        long rejected = bookingRepository.countByEventIdAndStatus(eventId, REJECTED);
        BigDecimal revenue = bookingRepository.sumConfirmedRevenue(eventId);
        if (revenue == null) {
            revenue = BigDecimal.ZERO;
        }
        revenue = revenue.setScale(2, RoundingMode.HALF_UP);

        int totalSeats = event.getTotalSeats();
        BigDecimal occupancy = totalSeats <= 0
                ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(seatsSold).multiply(HUNDRED)
                .divide(BigDecimal.valueOf(totalSeats), 1, RoundingMode.HALF_UP);

        return new EventSummaryResponse(
                event.getId(),
                event.getName(),
                totalSeats,
                seatsSold,
                totalSeats - seatsSold,
                confirmed,
                rejected,
                revenue,
                occupancy,
                bandFor(occupancy));
    }

    /** Band is taken from the occupancy_pct the client sees (already rounded to 1 decimal). */
    static DemandBand bandFor(BigDecimal occupancyPct) {
        if (occupancyPct.compareTo(LOW_BELOW) < 0) {
            return DemandBand.LOW;
        }
        if (occupancyPct.compareTo(HIGH_ABOVE) > 0) {
            return DemandBand.HIGH;
        }
        return DemandBand.MEDIUM;   // 40 to 75 inclusive
    }

    private Event requireEvent(long id) {
        return eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }
}
