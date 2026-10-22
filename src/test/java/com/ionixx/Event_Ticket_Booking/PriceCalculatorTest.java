package com.ionixx.Event_Ticket_Booking;

import com.ionixx.Event_Ticket_Booking.service.PriceCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceCalculatorTest {

    private final PriceCalculator calc = new PriceCalculator();
    private final LocalDate today = LocalDate.of(2026, 10, 10);

    @Test
    void earlyBirdAndGroup_workedExample() {
        BigDecimal p = calc.pricePerSeat(new BigDecimal("1500"), today.plusDays(71), today, 4);
        assertEquals(new BigDecimal("1282.50"), p);
        assertEquals(new BigDecimal("5130.00"), calc.total(p, 4));
    }

    @Test
    void earlyBirdOnly_exactly30Days() {
        assertEquals(new BigDecimal("1350.00"),
                calc.pricePerSeat(new BigDecimal("1500"), today.plusDays(30), today, 2));
    }

    @Test
    void groupOnly() {
        assertEquals(new BigDecimal("1425.00"),
                calc.pricePerSeat(new BigDecimal("1500"), today.plusDays(10), today, 4));
    }

    @Test
    void noDiscount() {
        assertEquals(new BigDecimal("1500.00"),
                calc.pricePerSeat(new BigDecimal("1500"), today.plusDays(29), today, 3));
    }
}
