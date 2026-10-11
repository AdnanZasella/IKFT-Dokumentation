package se.ikft.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import se.ikft.security.SecurityConfig;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.FelController.class)
@Import({ GlobalExceptionHandlerTest.FelController.class, SecurityConfig.class })
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

	record Namn(@NotBlank String namn) {
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

	}

}
