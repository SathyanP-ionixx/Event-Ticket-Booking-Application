package com.ionixx.Event_Ticket_Booking.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
public class Booking {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_id", nullable = false)
  private Long eventId;

  /** Owner of the booking; null for bookings made before login existed. */
  @Column(name = "user_id")
  private Long userId;

  @Column(name = "customer_name", nullable = false)
  private String customerName;

  @Column(name = "customer_email", nullable = false)
  private String customerEmail;

  @Column(nullable = false)
  private Integer seats;

  @Column(name = "price_per_seat")
  private BigDecimal pricePerSeat;

  @Column(name = "total_amount")
  private BigDecimal totalAmount;

  @Column(nullable = false)
  private String status;

  @Column(name = "rejection_reason")
  private String rejectionReason;

  @Column(name = "booked_on", nullable = false)
  private LocalDate bookedOn;

  @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("seatNumber ASC")
  private List<BookingSeat> bookingSeats = new ArrayList<>();

  public void addSeat(Integer seatNumber) {
    BookingSeat s = new BookingSeat();
    s.setBooking(this);
    s.setEventId(this.eventId);
    s.setSeatNumber(seatNumber);
    s.setStatus(this.status);
    bookingSeats.add(s);
  }

  public Long getId() {
    return id;
  }

  public Long getEventId() {
    return eventId;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
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

  public Integer getSeats() {
    return seats;
  }

  public void setSeats(Integer seats) {
    this.seats = seats;
  }

  public BigDecimal getPricePerSeat() {
    return pricePerSeat;
  }

  public void setPricePerSeat(BigDecimal pricePerSeat) {
    this.pricePerSeat = pricePerSeat;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public void setTotalAmount(BigDecimal totalAmount) {
    this.totalAmount = totalAmount;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  public void setRejectionReason(String rejectionReason) {
    this.rejectionReason = rejectionReason;
  }

  public LocalDate getBookedOn() {
    return bookedOn;
  }

  public void setBookedOn(LocalDate bookedOn) {
    this.bookedOn = bookedOn;
  }

  public List<BookingSeat> getBookingSeats() {
    return bookingSeats;
  }
}
