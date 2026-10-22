package com.ionixx.Event_Ticket_Booking.service;

import com.ionixx.Event_Ticket_Booking.dto.EventRequest;
import com.ionixx.Event_Ticket_Booking.dto.EventResponse;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.Category;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.DuplicateEventException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventNotFoundException;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock EventRepository repository;
    @InjectMocks EventService service;

    private EventRequest request(String category) {
        EventRequest r = new EventRequest();
        r.setName("  Chennai Jazz Night  ");
        r.setVenue("Music Academy, Chennai");
        r.setCategory(category);
        r.setEventDate("2099-12-20");
        r.setTotalSeats(200);
        r.setBasePrice(new BigDecimal("1500"));
        return r;
    }

    private Event existing(long id) {
        Event e = new Event();
        ReflectionTestUtils.setField(e, "id", id);
        e.setName("Chennai Jazz Night");
        e.setCategory(Category.MUSIC);
        e.setBasePrice(new BigDecimal("1500"));
        e.setTotalSeats(200);
        e.setEventDate(LocalDate.of(2099, 12, 20));
        e.setVenue("Music Academy, Chennai");
        return e;
    }

    @Test
    void create_mapsEveryField_trimsName_andUppercasesCategory() {
        when(repository.findByNameAndEventDate("Chennai Jazz Night", LocalDate.of(2099, 12, 20)))
                .thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        EventResponse r = service.create(request("music"));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(repository).saveAndFlush(captor.capture());
        Event saved = captor.getValue();
        assertEquals("Chennai Jazz Night", saved.getName());
        assertEquals(Category.MUSIC, saved.getCategory());
        assertEquals(new BigDecimal("1500"), saved.getBasePrice());
        assertEquals(200, saved.getTotalSeats());
        assertEquals(LocalDate.of(2099, 12, 20), saved.getEventDate());
        assertEquals("Music Academy, Chennai", saved.getVenue());

        assertEquals("Chennai Jazz Night", r.name());
        assertEquals(Category.MUSIC, r.category());
        assertEquals(LocalDate.of(2099, 12, 20), r.eventDate());
        assertEquals(200, r.totalSeats());
    }

    @Test
    void create_duplicate_throws_carryingTheExistingId_andDoesNotSave() {
        when(repository.findByNameAndEventDate("Chennai Jazz Night", LocalDate.of(2099, 12, 20)))
                .thenReturn(Optional.of(existing(9L)));

        DuplicateEventException ex = assertThrows(DuplicateEventException.class,
                () -> service.create(request("MUSIC")));

        assertEquals(9L, ex.getExistingEventId());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void getById_found() {
        when(repository.findById(3L)).thenReturn(Optional.of(existing(3L)));

        EventResponse r = service.getById(3L);

        assertEquals(3L, r.id());
        assertEquals("Chennai Jazz Night", r.name());
        assertEquals(Category.MUSIC, r.category());
    }

    @Test
    void getById_notFound_hasSpecMessage() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        EventNotFoundException ex = assertThrows(EventNotFoundException.class, () -> service.getById(42L));

        assertEquals("Event with id 42 not found", ex.getMessage());
    }
}
