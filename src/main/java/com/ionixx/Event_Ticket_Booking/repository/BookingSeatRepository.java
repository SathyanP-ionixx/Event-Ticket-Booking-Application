package com.ionixx.Event_Ticket_Booking.repository;


import com.ionixx.Event_Ticket_Booking.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    @Query("""
           select s.seatNumber from BookingSeat s
           where s.eventId = :eventId
             and s.status = 'CONFIRMED'
             and s.seatNumber in :seats
           order by s.seatNumber
           """)
    List<Integer> findConfirmedSeatNumbers(@Param("eventId") Long eventId,
                                           @Param("seats") Collection<Integer> seats);

    @Query("""
           select s.seatNumber from BookingSeat s
           where s.eventId = :eventId
             and s.status = 'CONFIRMED'
           order by s.seatNumber
           """)
    List<Integer> findAllConfirmedSeatNumbers(@Param("eventId") Long eventId);
}
