package com.ionixx.Event_Ticket_Booking.event.repository;

import com.ionixx.Event_Ticket_Booking.event.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface EventRepository extends JpaRepository<Event, Long> {
    boolean existsByNameAndEventDate(String name, LocalDate eventDate);
}
