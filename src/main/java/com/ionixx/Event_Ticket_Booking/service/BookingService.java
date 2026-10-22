package com.ionixx.Event_Ticket_Booking.service;


import com.ionixx.Event_Ticket_Booking.dto.BookingRequest;
import com.ionixx.Event_Ticket_Booking.dto.BookingResponse;
import com.ionixx.Event_Ticket_Booking.entity.Booking;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingNotFoundException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingValidationException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventNotFoundException;
import com.ionixx.Event_Ticket_Booking.repository.BookingRepository;
import com.ionixx.Event_Ticket_Booking.repository.BookingSeatRepository;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import com.ionixx.Event_Ticket_Booking.validators.BookingValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BookingService {

    private static final int MAX_SEATS_PER_CUSTOMER = 6;

    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final EventRepository eventRepository;
    private final BookingValidator validator;
    private final PriceCalculator priceCalculator;

    public BookingService(BookingRepository bookingRepository,
                          BookingSeatRepository bookingSeatRepository,
                          EventRepository eventRepository,
                          BookingValidator validator,
                          PriceCalculator priceCalculator) {
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.eventRepository = eventRepository;
        this.validator = validator;
        this.priceCalculator = priceCalculator;
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest req) {
        // 1. validate body (400)
        validator.validate(req);

        // 2. event existence (404) + seat range (400); row lock serializes concurrent bookings
        Event event = eventRepository.findByIdForUpdate(req.getEventId())
                .orElseThrow(() -> new EventNotFoundException(req.getEventId()));

        List<Integer> seatNumbers = req.getSeatNumbers().stream().sorted().toList();
        for (Integer s : seatNumbers) {
            if (s > event.getTotalSeats()) {
                throw new BookingValidationException("seat_numbers",
                        "seat " + s + " does not exist for this event");
            }
        }

        LocalDate today = LocalDate.now();

        Booking booking = new Booking();
        booking.setEventId(event.getId());
        booking.setCustomerName(req.getCustomerName());
        booking.setCustomerEmail(req.getCustomerEmail());
        booking.setSeats(seatNumbers.size());   // total number of seats booked
        booking.setBookedOn(today);

        // 3. booking rules - first failure decides
        String rejection = checkRules(event, req.getCustomerEmail(), seatNumbers, today);

        if (rejection != null) {
            booking.setStatus("REJECTED");
            booking.setRejectionReason(rejection);
        } else {
            // 4 & 5. pricing
            booking.setStatus("CONFIRMED");
            var perSeat = priceCalculator.pricePerSeat(event.getBasePrice(), event.getEventDate(),
                    today, seatNumbers.size());
            booking.setPricePerSeat(perSeat);
            booking.setTotalAmount(priceCalculator.total(perSeat, seatNumbers.size()));
        }

        // 6. save (rejected bookings are saved too)
        seatNumbers.forEach(booking::addSeat);
        Booking saved = bookingRepository.save(booking);

        // 7. 201 in both cases (controller)
        return BookingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long id) {
        Booking b = bookingRepository.findById(id)
                .orElseThrow(() -> new BookingNotFoundException("Booking not found with id " + id));
        return BookingResponse.from(b);
    }

    private String checkRules(Event event, String email, List<Integer> seatNumbers, LocalDate today) {
        // Date rule
        if (event.getEventDate().isBefore(today)) {
            return "Event is already over";
        }

        // Availability rule (query is ordered ascending)
        List<Integer> taken = bookingSeatRepository.findConfirmedSeatNumbers(event.getId(), seatNumbers);
        if (!taken.isEmpty()) {
            return "Seats already booked: " +
                    taken.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }

        // Per-customer rule
        long held = bookingRepository.countConfirmedSeatsForCustomer(event.getId(), email);
        if (held + seatNumbers.size() > MAX_SEATS_PER_CUSTOMER) {
            return "Customer already holds the maximum seats for this event";
        }
        return null;
    }
}
