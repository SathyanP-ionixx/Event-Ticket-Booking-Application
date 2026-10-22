package com.ionixx.Event_Ticket_Booking.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private final PriceCalculator calc = new PriceCalculator();

    private BigDecimal price(String base, int daysAhead, int seats) {
        return calc.pricePerSeat(new BigDecimal(base), TODAY.plusDays(daysAhead), TODAY, seats);
    }

    @Test
    void workedExample_71Days_4Seats_base1500() {
        BigDecimal perSeat = price("1500", 71, 4);
        assertEquals(new BigDecimal("1282.50"), perSeat);
        assertEquals(new BigDecimal("5130.00"), calc.total(perSeat, 4));
    }

    @Test
    void checklistExample_10Days_2Seats_base1000_noDiscount() {
        BigDecimal perSeat = price("1000", 10, 2);
        assertEquals(new BigDecimal("1000.00"), perSeat);
        assertEquals(new BigDecimal("2000.00"), calc.total(perSeat, 2));
    }

    @ParameterizedTest(name = "base={0} days={1} seats={2} -> {3}")
    @CsvSource({
            // early-bird boundary: 30 or more days
            "1000, 29, 1, 1000.00",
            "1000, 30, 1, 900.00",
            "1000, 31, 1, 900.00",
            "1000, 0,  1, 1000.00",
            // group boundary: 4 or more seats
            "1000, 10, 3, 1000.00",
            "1000, 10, 4, 950.00",
            "1000, 10, 6, 950.00",
            // both discounts, one after the other
            "1000, 71, 4, 855.00",
            "1500, 71, 4, 1282.50",
            // result is always 2 decimal places, even without discounts
            "1500, 10, 1, 1500.00",
            "99.99, 10, 1, 99.99",
            // HALF_UP rounding
            "99.99, 30, 1, 89.99",   // 89.991
            "10.05, 30, 1, 9.05",    // 9.045  -> up
            "10.10, 10, 4, 9.60",    // 9.595  -> up
            "99.99, 30, 4, 85.49"    // 85.49145
    })
    void pricePerSeat(String base, int daysAhead, int seats, String expected) {
        assertEquals(new BigDecimal(expected), price(base, daysAhead, seats));
    }

    @ParameterizedTest(name = "{0} x {1} = {2}")
    @CsvSource({
            "1282.50, 4, 5130.00",
            "333.33,  3, 999.99",
            "1000.00, 1, 1000.00",
            "950.00,  6, 5700.00"
    })
    void total(String perSeat, int seats, String expected) {
        assertEquals(new BigDecimal(expected), calc.total(new BigDecimal(perSeat), seats));
    }
}
