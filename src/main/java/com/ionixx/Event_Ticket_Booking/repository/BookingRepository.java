package com.ionixx.Event_Ticket_Booking.repository;


import com.ionixx.Event_Ticket_Booking.entity.Booking;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
           select coalesce(sum(b.seats), 0) from Booking b
           where b.eventId = :eventId
             and lower(b.customerEmail) = lower(:email)
             and b.status = 'CONFIRMED'
           """)
    long countConfirmedSeatsForCustomer(@Param("eventId") Long eventId,
                                        @Param("email") String email);

    @EntityGraph(attributePaths = "bookingSeats")
    List<Booking> findByEventIdOrderByBookedOnDescIdDesc(Long eventId);

    @EntityGraph(attributePaths = "bookingSeats")
    List<Booking> findByEventIdAndStatusOrderByBookedOnDescIdDesc(Long eventId, String status);

    // ---- API 7: aggregates done in SQL ----

    long countByEventIdAndStatus(Long eventId, String status);

    @Query("""
           select coalesce(sum(b.seats), 0) from Booking b
           where b.eventId = :eventId and b.status = 'CONFIRMED'
           """)
    long sumConfirmedSeats(@Param("eventId") Long eventId);

    /** null when the event has no confirmed bookings. */
    @Query("""
           select sum(b.totalAmount) from Booking b
           where b.eventId = :eventId and b.status = 'CONFIRMED'
           """)
    BigDecimal sumConfirmedRevenue(@Param("eventId") Long eventId);
}
