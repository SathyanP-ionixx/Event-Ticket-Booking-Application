package com.ionixx.Event_Ticket_Booking.controller;

import com.ionixx.Event_Ticket_Booking.dto.*;
import com.ionixx.Event_Ticket_Booking.filter.RequestIdFilter;
import com.ionixx.Event_Ticket_Booking.security.AuthUser;
import com.ionixx.Event_Ticket_Booking.service.EventReportService;
import com.ionixx.Event_Ticket_Booking.service.EventService;
import com.ionixx.Event_Ticket_Booking.util.IDparser;
import com.ionixx.Event_Ticket_Booking.util.PageParams;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class EventController {

  private final EventService service;
  private final EventReportService reportService;

  public EventController(EventService service, EventReportService reportService) {
    this.reportService = reportService;
    this.service = service;
  }

  // ----------------------Create an event-----------------------------
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public EventResponse create(
      @Valid @RequestBody EventRequest request, @AuthenticationPrincipal AuthUser user) {
    EventResponse created = service.create(request, user.id());
    // top-level "eventId" on the request's single log line
    MDC.put(RequestIdFilter.MDC_EVENT_ID, String.valueOf(created.id()));
    return created;
  }

  //  ---------------------Get All Events-----------------------
  @GetMapping("/getallevents")
  @ResponseStatus(HttpStatus.OK)
  public List<EventResponse> getAllEvents(@AuthenticationPrincipal AuthUser user) {

    MDC.put(RequestIdFilter.MDC_USER_ID, String.valueOf(user.id()));
    return service.fetchAllEvents();
  }

  // ------------------- Get an event-------------------------
  @GetMapping("/{id}")
  public EventResponse getById(@PathVariable("id") String rawId) {
    long id = IDparser.parsePositiveId(rawId);

    MDC.put(RequestIdFilter.MDC_EVENT_ID, String.valueOf(id));
    return service.getById(id);
  }

  // --------------------List bookings for an event--------------------------
  //  @GetMapping("/{id}/bookings")
  //  public List<BookingResponse> listBookings(
  //      @PathVariable("id") String rawId,
  //      @RequestParam(name = "status", required = false) String status,
  //      @AuthenticationPrincipal AuthUser user) {
  //    long id = IDparser.parsePositiveId(rawId);
  //
  //    MDC.put(RequestIdFilter.MDC_EVENT_ID, String.valueOf(id));
  //
  //    return reportService.listBookings(id, status, user);
  //  }
  @GetMapping("/{id}/bookings")
  public PagedResponse<BookingResponse> listBookings(
      @PathVariable("id") String rawId,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "page", required = false) String page,
      @RequestParam(name = "size", required = false) String size,
      @AuthenticationPrincipal AuthUser user) {
    long id = IDparser.parsePositiveId(rawId);
    PageParams params = PageParams.parse(page, size);

    MDC.put(RequestIdFilter.MDC_EVENT_ID, String.valueOf(id));

    return reportService.listBookings(id, status, params, user);
  }

  // -----------------------Seat availability-------------------------------
  @GetMapping("/{id}/seats")
  public SeatMapResponse seatMap(@PathVariable("id") String rawId) {
    long id = IDparser.parsePositiveId(rawId);

    MDC.put(RequestIdFilter.MDC_EVENT_ID, String.valueOf(id));
    return reportService.getSeatMap(id);
  }

  // ------------------------- Event sales summary ---------------------------
  @GetMapping("/{id}/summary")
  public EventSummaryResponse summary(
      @PathVariable("id") String rawId, @AuthenticationPrincipal AuthUser user) {
    long id = IDparser.parsePositiveId(rawId);

    MDC.put(RequestIdFilter.MDC_EVENT_ID, String.valueOf(id));
    return reportService.getSummary(id, user);
  }
}
