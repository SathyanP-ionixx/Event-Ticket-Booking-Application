package com.ionixx.Event_Ticket_Booking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

@Component
public class PriceCalculator {

  private static final BigDecimal EARLY_BIRD = new BigDecimal("0.90");
  private static final BigDecimal GROUP = new BigDecimal("0.95");

  public BigDecimal pricePerSeat(
      BigDecimal basePrice, LocalDate eventDate, LocalDate today, int seatCount) {
    BigDecimal price = basePrice;
    if (ChronoUnit.DAYS.between(today, eventDate) >= 30) {
      price = price.multiply(EARLY_BIRD);
    }
    if (seatCount >= 4) {
      price = price.multiply(GROUP);
    }
    return price.setScale(2, RoundingMode.HALF_UP);
  }

  public BigDecimal total(BigDecimal pricePerSeat, int seatCount) {
    return pricePerSeat.multiply(BigDecimal.valueOf(seatCount)).setScale(2, RoundingMode.HALF_UP);
  }
}
