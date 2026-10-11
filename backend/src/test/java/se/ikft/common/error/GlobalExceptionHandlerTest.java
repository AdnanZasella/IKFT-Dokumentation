package se.ikft.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.SQLException;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.Valid;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import se.ikft.security.SecurityConfig;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.FelController.class)
@Import({ GlobalExceptionHandlerTest.FelController.class, ProblemDetailErrorController.class, SecurityConfig.class })
@WithMockUser(roles = "DOCUMENTER")
class GlobalExceptionHandlerTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void valideringsfelGer400MedFaltlista() throws Exception {
		mockMvc.perform(post("/api/v1/test/validera").contentType(MediaType.APPLICATION_JSON).content("{\"namn\":\"\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.errors[0].field").value("namn"))
			.andExpect(jsonPath("$.errors[0].message").isNotEmpty());
	}

	@Test
	void notFoundExceptionGer404() throws Exception {
		mockMvc.perform(get("/api/v1/test/saknas"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Medlemmen finns inte."));
	}

	@Test
	void okandPathGer404() throws Exception {
		mockMvc.perform(get("/api/v1/test/finns-inte-alls"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void conflictExceptionGer409() throws Exception {
		mockMvc.perform(get("/api/v1/test/konflikt"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.detail").value("Finns redan."));
	}

	@Test
	void ovantatFelGer500UtanDetaljer() throws Exception {
		mockMvc.perform(get("/api/v1/test/krasch"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.trace").doesNotExist())
			.andExpect(content().string(not(containsString("hemlig detalj"))));
	}

	@Test
	void felRollGer403SomProblemDetail() throws Exception {
		mockMvc.perform(get("/api/v1/test/bara-admin"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	@WithAnonymousUser
	void anonymMotMetodskyddGer401() throws Exception {
		mockMvc.perform(get("/api/v1/public/test/bara-inloggad"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void felPaHelaObjektetFinnsIFaltlistan() throws Exception {
		mockMvc
			.perform(post("/api/v1/test/period").contentType(MediaType.APPLICATION_JSON)
				.content("{\"start\":5,\"slut\":1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("period"))
			.andExpect(jsonPath("$.errors[0].message").value("Slut måste komma efter start."));
	}

	@Test
	void responseStatusAnnoteringRespekteras() throws Exception {
		mockMvc.perform(get("/api/v1/test/annoterad"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Felaktig begäran."));
	}

	@Test
	void dataIntegrityViolationGer409UtanDetaljer() throws Exception {
		mockMvc.perform(get("/api/v1/test/dubblett"))
			.andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(content().string(not(containsString("user_account_username_key"))));
	}

	@Test
	void ogiltigtVardeIDatabasenGer400UtanDetaljer() throws Exception {
		mockMvc.perform(get("/api/v1/test/ogiltigt-varde"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Begäran innehåller ogiltiga värden."))
			.andExpect(content().string(not(containsString("0x00"))));
	}

	@Test
	void felsidanSvararMedProblemDetail() throws Exception {
		mockMvc.perform(get("/error").with(request -> {
			request.setDispatcherType(DispatcherType.ERROR);
			request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 400);
			return request;
		}))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(400));
	}

	record Namn(@NotBlank String namn) {
	}

	@SlutEfterStart
	record Period(int start, int slut) {
	}

	@Target(ElementType.TYPE)
	@Retention(RetentionPolicy.RUNTIME)
	@Constraint(validatedBy = SlutEfterStartValidator.class)
	@interface SlutEfterStart {

		String message() default "Slut måste komma efter start.";

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};

	}

	static class SlutEfterStartValidator implements ConstraintValidator<SlutEfterStart, Period> {

		@Override
		public boolean isValid(Period period, ConstraintValidatorContext context) {
			return period.slut() > period.start();
		}

	}

	@ResponseStatus(code = HttpStatus.BAD_REQUEST, reason = "Felaktig begäran.")
	static class AnnoteratException extends RuntimeException {
	}

	@RestController
	static class FelController {

		@PostMapping("/api/v1/test/validera")
		String validera(@Valid @RequestBody Namn body) {
			return body.namn();
		}

		@GetMapping("/api/v1/test/saknas")
		String saknas() {
			throw new NotFoundException("Medlemmen finns inte.");
		}

		@GetMapping("/api/v1/test/konflikt")
		String konflikt() {
			throw new ConflictException("Finns redan.");
		}

		@GetMapping("/api/v1/test/krasch")
		String krasch() {
			throw new IllegalStateException("hemlig detalj");
		}

		@GetMapping("/api/v1/test/bara-admin")
		@PreAuthorize("hasRole('ADMIN')")
		String baraAdmin() {
			return "ok";
		}

		@GetMapping("/api/v1/public/test/bara-inloggad")
		@PreAuthorize("isAuthenticated()")
		String baraInloggad() {
			return "ok";
		}

		@PostMapping("/api/v1/test/period")
		String period(@Valid @RequestBody Period body) {
			return "ok";
		}

		@GetMapping("/api/v1/test/annoterad")
		String annoterad() {
			throw new AnnoteratException();
		}

		@GetMapping("/api/v1/test/dubblett")
		String dubblett() {
			throw new DataIntegrityViolationException("duplicate key value violates unique constraint "
					+ "\"user_account_username_key\"");
		}

		@GetMapping("/api/v1/test/ogiltigt-varde")
		String ogiltigtVarde() {
			throw new DataIntegrityViolationException("could not execute statement",
					new SQLException("invalid byte sequence for encoding \"UTF8\": 0x00", "22021"));
		}

	}

}
