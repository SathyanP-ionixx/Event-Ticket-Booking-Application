package com.ionixx.Event_Ticket_Booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ionixx.Event_Ticket_Booking.dto.EventRequest;
import com.ionixx.Event_Ticket_Booking.dto.EventResponse;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.Category;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.DuplicateEventException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventNotFoundException;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import com.ionixx.Event_Ticket_Booking.support.TestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class EventServiceTest {

  private final EventRepository repository = mock(EventRepository.class);
  private final EventService service = new EventService(repository);

  private static EventRequest request() {
    EventRequest r = new EventRequest();
    r.setName("  Rock Night ");
    r.setCategory("music");
    r.setBasePrice(new BigDecimal("150.00"));
    r.setTotalSeats(120);
    r.setEventDate("2099-12-31");
    r.setVenue("Main Hall");
    return r;
  }

  @Test
  void create_savesTheEventWithItsOrganiser() {
    when(repository.findByNameAndEventDate("Rock Night", LocalDate.of(2099, 12, 31)))
        .thenReturn(Optional.empty());
    when(repository.saveAndFlush(any(Event.class)))
        .thenAnswer(
            inv -> {
              Event e = inv.getArgument(0);
              ReflectionTestUtils.setField(e, "id", 11L);
              return e;
            });

    EventResponse created = service.create(request(), 7L);

    assertThat(created.id()).isEqualTo(11L);
    assertThat(created.name()).isEqualTo("Rock Night");
    assertThat(created.category()).isEqualTo(Category.MUSIC);
    assertThat(created.basePrice()).isEqualByComparingTo("150.00");
    assertThat(created.totalSeats()).isEqualTo(120);
    assertThat(created.eventDate()).isEqualTo(LocalDate.of(2099, 12, 31));
    assertThat(created.venue()).isEqualTo("Main Hall");

    ArgumentCaptor<Event> saved = ArgumentCaptor.forClass(Event.class);
    verify(repository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getOrganiserId()).isEqualTo(7L);
  }

  @Test
  void create_duplicate_reportsTheExistingEvent() {
    Event existing = TestData.event(5L, 10, LocalDate.of(2099, 12, 31), "10.00", 7L);
    when(repository.findByNameAndEventDate("Rock Night", LocalDate.of(2099, 12, 31)))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.create(request(), 7L))
        .isInstanceOfSatisfying(
            DuplicateEventException.class, ex -> assertThat(ex.getExistingEventId()).isEqualTo(5L));
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void getById_found() {
    when(repository.findById(3L))
        .thenReturn(Optional.of(TestData.event(3L, 10, LocalDate.now(), "10.00", 1L)));

    assertThat(service.getById(3L).id()).isEqualTo(3L);
  }

  @Test
  void getById_missing() {
    when(repository.findById(3L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getById(3L)).isInstanceOf(EventNotFoundException.class);
  }
}
