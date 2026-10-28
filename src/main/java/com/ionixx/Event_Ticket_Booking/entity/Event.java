package com.ionixx.Event_Ticket_Booking.entity;

import com.ionixx.Event_Ticket_Booking.enums.Category;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "events",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_event_name_date",
            columnNames = {"name", "event_date"}))
public class Event {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Category category;

  @Column(name = "base_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal basePrice;

  @Column(name = "total_seats", nullable = false)
  private Integer totalSeats;

  @Column(name = "event_date", nullable = false)
  private LocalDate eventDate;

  @Column(nullable = false, length = 100)
  private String venue;

  @Column(name = "organiser_id")
  private Long organiserId;

  public Long getOrganiserId() {
    return organiserId;
  }

  public void setOrganiserId(Long organiserId) {
    this.organiserId = organiserId;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Category getCategory() {
    return category;
  }

  public void setCategory(Category category) {
    this.category = category;
  }

  public BigDecimal getBasePrice() {
    return basePrice;
  }

  public void setBasePrice(BigDecimal basePrice) {
    this.basePrice = basePrice;
  }

  public Integer getTotalSeats() {
    return totalSeats;
  }

  public void setTotalSeats(Integer totalSeats) {
    this.totalSeats = totalSeats;
  }

  public LocalDate getEventDate() {
    return eventDate;
  }

  public void setEventDate(LocalDate eventDate) {
    this.eventDate = eventDate;
  }

  public String getVenue() {
    return venue;
  }

  public void setVenue(String venue) {
    this.venue = venue;
  }
}
