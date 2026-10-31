package vn.racehorse.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

/** A date inside the window of {@link Bounds#DATE_MIN} to {@link Bounds#DATE_MAX}; an empty date passes (other rules decide). */
@Documented
@Constraint(validatedBy = ClubDate.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ClubDate {

    String message() default "Ngày không hợp lệ.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ClubDate, LocalDate> {

        private static final LocalDate MIN = LocalDate.parse(Bounds.DATE_MIN);
        private static final LocalDate MAX = LocalDate.parse(Bounds.DATE_MAX);

        @Override
        public boolean isValid(LocalDate value, ConstraintValidatorContext context) {
            if (value == null || (!value.isBefore(MIN) && !value.isAfter(MAX))) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(value.isBefore(MIN)
                    ? "Ngày không được trước 01/01/1990." : "Ngày không được sau 31/12/2100.").addConstraintViolation();
            return false;
        }
    }
}
