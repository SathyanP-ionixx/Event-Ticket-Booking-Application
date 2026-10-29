package com.ionixx.Event_Ticket_Booking.repository;

import com.ionixx.Event_Ticket_Booking.entity.Booking;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

  @Query(
      """
           select coalesce(sum(b.seats), 0) from Booking b
           where b.eventId = :eventId
             and lower(b.customerEmail) = lower(:email)
             and b.status = 'CONFIRMED'
           """)
  long countConfirmedSeatsForCustomer(@Param("eventId") Long eventId, @Param("email") String email);

  // ---- paged booking lists ----
  // Newest first; id is the unique tie-breaker, so the order (and therefore every page) is stable.
  // No @EntityGraph: a collection fetch join cannot be paged in SQL (Hibernate would page in
  // memory).
  // The seats come from Booking.bookingSeats' @BatchSize: one extra query per page.

  Page<Booking> findByEventIdOrderByBookedOnDescIdDesc(Long eventId, Pageable pageable);

  Page<Booking> findByEventIdAndStatusOrderByBookedOnDescIdDesc(
      Long eventId, String status, Pageable pageable);

  // ---- API 7: aggregates done in SQL ----

  long countByEventIdAndStatus(Long eventId, String status);

  @Query(
      """
           select coalesce(sum(b.seats), 0) from Booking b
           where b.eventId = :eventId and b.status = 'CONFIRMED'
           """)
  long sumConfirmedSeats(@Param("eventId") Long eventId);

  /** null when the event has no confirmed bookings. */
  @Query(
      """
           select sum(b.totalAmount) from Booking b
           where b.eventId = :eventId and b.status = 'CONFIRMED'
           """)
  BigDecimal sumConfirmedRevenue(@Param("eventId") Long eventId);
}
