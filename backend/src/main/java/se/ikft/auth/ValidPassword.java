package se.ikft.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Lösenordet följer {@link Passwords}-reglerna med minsta längd från {@link AuthProperties}. Tomma
 * värden godkänns, de fångas av {@code @NotBlank}.
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPassword.Validator.class)
@interface ValidPassword {

	String message() default "Lösenordet uppfyller inte kraven.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

	/** Skapas av Spring, som injicerar inställningarna. */
	class Validator implements ConstraintValidator<ValidPassword, String> {

		private final AuthProperties properties;

		Validator(AuthProperties properties) {
			this.properties = properties;
		}

		@Override
		public boolean isValid(String password, ConstraintValidatorContext context) {
			if (password == null || password.isBlank()) {
				return true;
			}
			return Passwords.problem(password, properties.passwordMinLength()).map(message -> {
				context.disableDefaultConstraintViolation();
				context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
				return false;
			}).orElse(true);
		}

	}

}
