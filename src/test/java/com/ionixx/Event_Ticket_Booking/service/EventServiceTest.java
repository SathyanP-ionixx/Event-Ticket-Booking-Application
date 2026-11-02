package com.ionixx.Event_Ticket_Booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ionixx.Event_Ticket_Booking.dto.EventRequest;
import com.ionixx.Event_Ticket_Booking.dto.EventResponse;
import com.ionixx.Event_Ticket_Booking.entity.Event;
import com.ionixx.Event_Ticket_Booking.enums.Category;
import com.ionixx.Event_Ticket_Booking.enums.Role;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.BookingValidationException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.DuplicateEventException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventAlreadyOverException;
import com.ionixx.Event_Ticket_Booking.globalExceptionHandler.EventNotFoundException;
import com.ionixx.Event_Ticket_Booking.repository.BookingSeatRepository;
import com.ionixx.Event_Ticket_Booking.repository.EventRepository;
import com.ionixx.Event_Ticket_Booking.security.AuthUser;
import com.ionixx.Event_Ticket_Booking.support.TestData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class EventServiceTest {

  private static final AuthUser OWNER = new AuthUser(7L, "owner@test.com", Role.ORGANISER);
  private static final AuthUser OTHER = new AuthUser(8L, "other@test.com", Role.ORGANISER);
  private static final LocalDate NEW_DATE = LocalDate.of(2099, 6, 1);

  private final EventRepository repository = mock(EventRepository.class);
  private final BookingSeatRepository seatRepository = mock(BookingSeatRepository.class);
  private final EventService service = new EventService(repository, seatRepository);

  // ------------------------------------------------------------------ create / getById

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

  // ------------------------------------------------------------------ update

  private static EventRequest updateRequest(int totalSeats) {
    EventRequest r = new EventRequest();
    r.setName("  New Name ");
    r.setCategory("tech");
    r.setBasePrice(new BigDecimal("250.50"));
    r.setTotalSeats(totalSeats);
    r.setEventDate(NEW_DATE.toString());
    r.setVenue("New Venue");
    return r;
  }

  /** The stored event (id 5, 20 seats, "Old Name"), found through the locking query. */
  private Event storedEvent(Long organiserId, LocalDate date) {
    Event e = TestData.event(5L, 20, date, "100.00", organiserId);
    e.setName("Old Name");
    when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(e));
    when(repository.saveAndFlush(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
    return e;
  }

  @Test
  void update_replacesEveryEditableField_andKeepsTheOwner() {
    Event stored = storedEvent(7L, LocalDate.now().plusDays(40));

    EventResponse updated = service.update(5L, updateRequest(50), OWNER);

    assertThat(updated.id()).isEqualTo(5L);
    assertThat(updated.name()).isEqualTo("New Name");
    assertThat(updated.category()).isEqualTo(Category.TECH);
    assertThat(updated.basePrice()).isEqualByComparingTo("250.50");
    assertThat(updated.totalSeats()).isEqualTo(50);
    assertThat(updated.eventDate()).isEqualTo(NEW_DATE);
    assertThat(updated.venue()).isEqualTo("New Venue");
    assertThat(stored.getOrganiserId()).as("owner never changes").isEqualTo(7L);
    verify(repository).saveAndFlush(stored);
  }

  @Test
  void update_unknownEvent_isNotFound() {
    when(repository.findByIdForUpdate(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(5L, updateRequest(50), OWNER))
        .isInstanceOf(EventNotFoundException.class)
        .hasMessage("Event with id 5 not found");
  }

  @Test
  void update_byAnotherOrganiser_isForbidden_andNothingIsTouched() {
    Event stored = storedEvent(7L, LocalDate.now().plusDays(40));

    assertThatThrownBy(() -> service.update(5L, updateRequest(50), OTHER))
        .isInstanceOf(AccessDeniedException.class);

    assertThat(stored.getName()).isEqualTo("Old Name");
    verify(repository, never()).saveAndFlush(any());
    verifyNoInteractions(seatRepository);
  }

  @Test
  void update_eventWithoutAnOwner_isForbiddenToEveryone() {
    storedEvent(null, LocalDate.now().plusDays(40));

    assertThatThrownBy(() -> service.update(5L, updateRequest(50), OWNER))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void update_eventAlreadyOver_isRefused() {
    Event stored = storedEvent(7L, LocalDate.now().minusDays(1));

    assertThatThrownBy(() -> service.update(5L, updateRequest(50), OWNER))
        .isInstanceOf(EventAlreadyOverException.class)
        .hasMessage("Event is already over and can no longer be edited");
    assertThat(stored.getName()).isEqualTo("Old Name");
  }

  @Test
  void update_eventHappeningToday_isStillEditable() {
    storedEvent(7L, LocalDate.now());

    assertThat(service.update(5L, updateRequest(50), OWNER).name()).isEqualTo("New Name");
  }

  @Test
  void update_cannotShrinkBelowTheHighestConfirmedSeat() {
    Event stored = storedEvent(7L, LocalDate.now().plusDays(40));
    when(seatRepository.findMaxConfirmedSeatNumber(5L)).thenReturn(30);

    assertThatThrownBy(() -> service.update(5L, updateRequest(25), OWNER))
        .isInstanceOfSatisfying(
            BookingValidationException.class,
            ex ->
                assertThat(ex.getDetails())
                    .singleElement()
                    .satisfies(
                        d -> {
                          assertThat(d.field()).isEqualTo("total_seats");
                          assertThat(d.message())
                              .isEqualTo("must be at least 30 (seat 30 is already booked)");
                        }));
    assertThat(stored.getTotalSeats()).as("nothing half-applied").isEqualTo(20);
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void update_canShrinkExactlyToTheHighestConfirmedSeat() {
    storedEvent(7L, LocalDate.now().plusDays(40));
    when(seatRepository.findMaxConfirmedSeatNumber(5L)).thenReturn(30);

    assertThat(service.update(5L, updateRequest(30), OWNER).totalSeats()).isEqualTo(30);
  }

  @Test
  void update_withNoBookings_canUseAnySeatCount() {
    storedEvent(7L, LocalDate.now().plusDays(40));
    when(seatRepository.findMaxConfirmedSeatNumber(5L)).thenReturn(null);

    assertThat(service.update(5L, updateRequest(10), OWNER).totalSeats()).isEqualTo(10);
  }

  @Test
  void update_toTheNameAndDateOfAnotherEvent_isADuplicate_andNothingChanges() {
    Event stored = storedEvent(7L, LocalDate.now().plusDays(40));
    when(repository.findByNameAndEventDate("New Name", NEW_DATE))
        .thenReturn(Optional.of(TestData.event(9L, 10, NEW_DATE, "10.00", 7L)));

    assertThatThrownBy(() -> service.update(5L, updateRequest(50), OWNER))
        .isInstanceOfSatisfying(
            DuplicateEventException.class, ex -> assertThat(ex.getExistingEventId()).isEqualTo(9L));
    assertThat(stored.getName()).isEqualTo("Old Name");
    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void update_keepingTheSameNameAndDate_isNotADuplicateOfItself() {
    Event stored = storedEvent(7L, NEW_DATE);
    when(repository.findByNameAndEventDate("New Name", NEW_DATE)).thenReturn(Optional.of(stored));

    assertThat(service.update(5L, updateRequest(50), OWNER).venue()).isEqualTo("New Venue");
  }
}
