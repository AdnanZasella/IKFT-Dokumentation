package se.ikft.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import se.ikft.TestcontainersConfiguration;
import se.ikft.security.AccessTokens;

/**
 * {@code GET /api/v1/auth/me} mot en riktig Postgres.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MeIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@BeforeEach
	void forbered() {
		aterstall();
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
	}

	@AfterEach
	void aterstall() {
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0, holder_member_id = NULL").update();
		jdbc.sql("DELETE FROM member").update();
	}

	@Test
	void adminUtanInnehavare() throws Exception {
		me("admin").andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.holderName").isEmpty());
	}

	@Test
	void dokumenterareMedInnehavare() throws Exception {
		jdbc.sql("""
				INSERT INTO member (id, first_name, last_name, birth_date, home_municipality)
				VALUES ('00000000-0000-0000-0000-000000000002', 'Prova', 'Provsson', '1985-06-15', 'Trollhättan')
				""").update();
		jdbc.sql("""
				UPDATE user_account SET holder_member_id = '00000000-0000-0000-0000-000000000002'
				WHERE username = 'dokumenterare'
				""").update();

		me("dokumenterare").andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("DOCUMENTER"))
			.andExpect(jsonPath("$.holderName").value("Prova Provsson"));
	}

	@Test
	void anonymGer401() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
	}

	private ResultActions me(String username) throws Exception {
		Cookie cookie = mockMvc
			.perform(post("/api/v1/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content("""
					{"username": "%s", "password": "%s"}
					""".formatted(username, LOSENORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie(AccessTokens.COOKIE_NAME);
		return mockMvc.perform(get("/api/v1/auth/me").cookie(cookie));
	}

}
