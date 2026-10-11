package se.ikft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Hela appen mot en riktig Postgres: migreringen och de grundläggande säkerhetsreglerna.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class GrundIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Test
	void migreringenSkaparDeTvaKontonaUtanLosenord() {
		List<Map<String, Object>> konton = jdbc
			.sql("SELECT username, role, password_hash, token_version FROM user_account ORDER BY username")
			.query()
			.listOfRows();

		assertThat(konton).extracting(rad -> rad.get("username")).containsExactly("admin", "dokumenterare");
		assertThat(konton).extracting(rad -> rad.get("role")).containsExactly("ADMIN", "DOCUMENTER");
		assertThat(konton).allSatisfy(rad -> {
			assertThat(rad.get("password_hash")).isNull();
			assertThat(rad.get("token_version")).isEqualTo(0);
		});
	}

	@Test
	void ingenKolumnForPersonnummer() {
		long antal = jdbc
			.sql("""
					SELECT count(*) FROM information_schema.columns
					WHERE table_schema = 'public'
					  AND (column_name ILIKE '%personnummer%' OR column_name ILIKE '%ssn%'
					       OR column_name ILIKE '%personal_id%')
					""")
			.query(Long.class)
			.single();

		assertThat(antal).isZero();
	}

	@Test
	void anonymMotSkyddadPathGer401SomProblemDetail() throws Exception {
		mockMvc.perform(get("/api/v1/members"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void publikPathKraverIngenInloggning() throws Exception {
		// Ingen publik endpoint finns än, så 404 visar att begäran släpptes igenom.
		mockMvc.perform(get("/api/v1/public/finns-inte"))
			.andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void healthArOppen() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void okandPathUtanforApietNekas() throws Exception {
		mockMvc.perform(get("/annat")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
	}

}
