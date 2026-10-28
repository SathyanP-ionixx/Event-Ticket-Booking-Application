package com.ionixx.Event_Ticket_Booking.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = FutureIsoDateValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FutureIsoDate {
  String message() default "must be a future date in format yyyy-mm-dd";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
