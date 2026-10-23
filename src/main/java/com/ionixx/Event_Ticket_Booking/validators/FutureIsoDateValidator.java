package com.ionixx.Event_Ticket_Booking.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class FutureIsoDateValidator implements ConstraintValidator<FutureIsoDate, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) {
            return true; // @NotNull reports missing values
        }
        LocalDate date;
        try {
            date = LocalDate.parse(value); // strict ISO yyyy-MM-dd
        } catch (DateTimeParseException e) {
            return fail(ctx, "must be in format yyyy-mm-dd");
        }
        if (!date.isAfter(LocalDate.now())) {
            return fail(ctx, "must be a future date");
        }
        return true;
    }

    private boolean fail(ConstraintValidatorContext ctx, String message) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
