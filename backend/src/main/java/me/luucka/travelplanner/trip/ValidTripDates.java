package me.luucka.travelplanner.trip;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

import static java.lang.annotation.ElementType.TYPE;

@Target(TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidTripDates.Validator.class)
@interface ValidTripDates {
    String message() default "End date must not be before start date and the trip must last at most 365 days";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidTripDates, TripRequest> {
        @Override
        public boolean isValid(TripRequest r, ConstraintValidatorContext ctx) {
            if (r.startDate() == null || r.endDate() == null) {
                return true; // @NotNull reports it
            }
            long days = ChronoUnit.DAYS.between(r.startDate(), r.endDate());
            if (days >= 0 && days <= 365) {
                return true;
            }
            // Report under the field so the API's errors map carries it
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("endDate").addConstraintViolation();
            return false;
        }
    }
}
