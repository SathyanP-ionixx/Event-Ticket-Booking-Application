package com.ionixx.Event_Ticket_Booking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ionixx.Event_Ticket_Booking.entity.Booking;
import com.ionixx.Event_Ticket_Booking.entity.BookingSeat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class BookingResponse {

  private Long id;

  @JsonProperty("event_id")
  private Long eventId;

  @JsonProperty("customer_name")
  private String customerName;

  @JsonProperty("customer_email")
  private String customerEmail;

  @JsonProperty("seat_numbers")
  private List<Integer> seatNumbers;

  private Integer seats;

  @JsonProperty("price_per_seat")
  private BigDecimal pricePerSeat;

  @JsonProperty("total_amount")
  private BigDecimal totalAmount;

  private String status;

  @JsonProperty("rejection_reason")
  private String rejectionReason;

  @JsonProperty("booked_on")
  private LocalDate bookedOn;

  public BookingResponse(
      Long id,
      Long eventId,
      String customerName,
      String customerEmail,
      List<Integer> seatNumbers,
      Integer seats,
      BigDecimal pricePerSeat,
      BigDecimal totalAmount,
      String status,
      String rejectionReason,
      LocalDate bookedOn) {
    this.id = id;
    this.eventId = eventId;
    this.customerName = customerName;
    this.customerEmail = customerEmail;
    this.seatNumbers = seatNumbers;
    this.seats = seats;
    this.pricePerSeat = pricePerSeat;
    this.totalAmount = totalAmount;
    this.status = status;
    this.rejectionReason = rejectionReason;
    this.bookedOn = bookedOn;
  }

  public static BookingResponse from(Booking b) {

    return new BookingResponse(
        b.getId(),
        b.getEventId(),
        b.getCustomerName(),
        b.getCustomerEmail(),
        b.getBookingSeats().stream().map(BookingSeat::getSeatNumber).sorted().toList(),
        b.getSeats(),
        b.getPricePerSeat(),
        b.getTotalAmount(),
        b.getStatus(),
        b.getRejectionReason(),
        b.getBookedOn());
  }

  public Long getId() {
    return id;
  }

  public Long getEventId() {
    return eventId;
  }

  public String getCustomerName() {
    return customerName;
  }

  public String getCustomerEmail() {
    return customerEmail;
  }

  public List<Integer> getSeatNumbers() {
    return seatNumbers;
  }

  public Integer getSeats() {
    return seats;
  }

  public BigDecimal getPricePerSeat() {
    return pricePerSeat;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getStatus() {
    return status;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public LocalDate getBookedOn() {
    return bookedOn;
  }
}
