package com.ionixx.Event_Ticket_Booking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class BookingRequest {

  @JsonProperty("event_id")
  private Long eventId;

  @JsonProperty("customer_name")
  private String customerName;

  @JsonProperty("customer_email")
  private String customerEmail;

  @JsonProperty("seat_numbers")
  private List<Integer> seatNumbers;

  public Long getEventId() {
    return eventId;
  }

  public void setEventId(Long eventId) {
    this.eventId = eventId;
  }

  public String getCustomerName() {
    return customerName;
  }

  public void setCustomerName(String customerName) {
    this.customerName = customerName;
  }

  public String getCustomerEmail() {
    return customerEmail;
  }

  public void setCustomerEmail(String customerEmail) {
    this.customerEmail = customerEmail;
  }

  public List<Integer> getSeatNumbers() {
    return seatNumbers;
  }

  public void setSeatNumbers(List<Integer> seatNumbers) {
    this.seatNumbers = seatNumbers;
  }
}
