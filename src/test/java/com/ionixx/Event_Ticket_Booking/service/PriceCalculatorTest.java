package com.ionixx.Event_Ticket_Booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PriceCalculatorTest {

  private static final LocalDate TODAY = LocalDate.of(2030, 1, 1);
  private final PriceCalculator calculator = new PriceCalculator();

  @Test
  void noDiscount_whenLessThan30DaysAway_andFewerThan4Seats() {
    BigDecimal price =
        calculator.pricePerSeat(new BigDecimal("100.00"), TODAY.plusDays(29), TODAY, 3);
    assertThat(price).isEqualByComparingTo("100.00");
  }

  @Test
  void earlyBird_appliesFrom30DaysOnward() {
    BigDecimal price =
        calculator.pricePerSeat(new BigDecimal("100.00"), TODAY.plusDays(30), TODAY, 1);
    assertThat(price).isEqualByComparingTo("90.00");
  }

  @Test
  void groupDiscount_appliesFrom4Seats() {
    BigDecimal price =
        calculator.pricePerSeat(new BigDecimal("100.00"), TODAY.plusDays(5), TODAY, 4);
    assertThat(price).isEqualByComparingTo("95.00");
  }

  @Test
  void bothDiscounts_stack() {
    BigDecimal price =
        calculator.pricePerSeat(new BigDecimal("100.00"), TODAY.plusDays(60), TODAY, 6);
    assertThat(price).isEqualByComparingTo("85.50");
  }

  @Test
  void roundsHalfUpToTwoDecimals() {
    // 33.33 * 0.90 = 29.997 -> 30.00
    assertThat(calculator.pricePerSeat(new BigDecimal("33.33"), TODAY.plusDays(40), TODAY, 1))
        .isEqualByComparingTo("30.00");
    // 10.05 * 0.95 = 9.5475 -> 9.55
    assertThat(calculator.pricePerSeat(new BigDecimal("10.05"), TODAY.plusDays(1), TODAY, 4))
        .isEqualByComparingTo("9.55");
  }

  @Test
  void total_multipliesAndKeepsTwoDecimals() {
    BigDecimal total = calculator.total(new BigDecimal("85.50"), 4);
    assertThat(total).isEqualByComparingTo("342.00");
    assertThat(total.scale()).isEqualTo(2);
  }
}
