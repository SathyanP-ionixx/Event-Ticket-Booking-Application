package com.ionixx.Event_Ticket_Booking.repository;


import com.ionixx.Event_Ticket_Booking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
           select coalesce(sum(b.seats), 0) from Booking b
           where b.eventId = :eventId
             and lower(b.customerEmail) = lower(:email)
             and b.status = 'CONFIRMED'
           """)
    long countConfirmedSeatsForCustomer(@Param("eventId") Long eventId,
                                        @Param("email") String email);
}
