package se.ikft.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import se.ikft.TestcontainersConfiguration;

/**
 * Det som E2E-testerna i {@code e2e/} bygger på: med profilen {@code e2e} har kontona kända lösenord och databasen har
 * påhittade medlemmar. Kontexten har profilen aktiv och får därför en egen databas, så den rör inga andra tester.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class E2eDataInitializerIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	E2eDataInitializer initializer;

	@Test
	void adminKanLoggaInMedDetKandaLosenordet() throws Exception {
		login("admin", E2eDataInitializer.ADMIN_PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.mustChangePassword").value(false));
	}

	@Test
	void dokumenterareKanLoggaInMedDetKandaLosenordetUtanInnehavare() throws Exception {
		login("dokumenterare", E2eDataInitializer.DOCUMENTER_PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("DOCUMENTER"))
			.andExpect(jsonPath("$.holderName").isEmpty())
			.andExpect(jsonPath("$.mustChangePassword").value(false));
	}

	@Test
	void ettFelLosenordFungerarInte() throws Exception {
		login("admin", E2eDataInitializer.DOCUMENTER_PASSWORD).andExpect(status().isUnauthorized());
	}

	@Test
	void ingenEngangskodSkapasForAdmin() {
		// Lösenordet sätts före AdminSetupCodeInitializer, som då inte skapar någon kod att läsa ur loggen.
		assertThat(jdbc.sql("SELECT count(*) FROM setup_code").query(Long.class).single()).isZero();
	}

	@Test
	void databasenHarDePahittadeMedlemmarnaOchIngenDubblettEfterNyKorning() {
		initializer.run(null);

		List<String> namn = jdbc.sql("SELECT first_name || ' ' || last_name FROM member ORDER BY first_name")
			.query(String.class)
			.list();
		assertThat(namn).containsExactly("Berit Exempelsson", "Cemal Provsson", "Dina Testdotter");
	}

	private ResultActions login(String username, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").with(csrfToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"username": "%s", "password": "%s"}
					""".formatted(username, password)));
	}

}
