package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.time.Instant;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import se.ikft.TestcontainersConfiguration;
import se.ikft.security.AccessTokens;

/**
 * {@code POST /api/v1/accounts/{id}/setup-code} mot en riktig Postgres.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AccountSetupCodeIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	private static final String NYTT = "ett-helt-nytt-losenord";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	java.time.Clock clock;

	Cookie admin;

	Cookie dokumenterare;

	@BeforeEach
	void forbered() throws Exception {
		stada();
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
		admin = loggaIn("admin");
		dokumenterare = loggaIn("dokumenterare");
	}

	@AfterEach
	void stada() {
		jdbc.sql("DELETE FROM setup_code").update();
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0, holder_member_id = NULL").update();
	}

	@Test
	void adminFarEnKodMedGiltighetstid() throws Exception {
		Instant fore = Instant.now();

		String svar = skapa(admin, kontoId("dokumenterare")).andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.code").isString())
			.andExpect(jsonPath("$.validUntil").isString())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(svar).containsPattern("\"code\":\"[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}\"");
		Instant giltigTill = jdbc.sql("SELECT valid_until FROM setup_code").query(java.sql.Timestamp.class).single()
			.toInstant();
		assertThat(giltigTill).isAfter(fore);
	}

	@Test
	void svaretKanInteCachas() throws Exception {
		skapa(admin, kontoId("dokumenterare")).andExpect(status().isOk())
			.andExpect(result -> assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store"));
	}

	@Test
	void kontotByterInteInnehavareOchBehallerLosenordetTillsKodenAnvands() throws Exception {
		String hashFore = hash("dokumenterare");

		skapa(admin, kontoId("dokumenterare")).andExpect(status().isOk());

		assertThat(hash("dokumenterare")).isEqualTo(hashFore);
		assertThat(tokenversion("dokumenterare")).isZero();
		assertThat(jdbc.sql("SELECT holder_member_id FROM user_account WHERE username = 'dokumenterare'")
			.query(UUID.class)
			.list()
			.getFirst()).isNull();
		// Den gamla sessionen och det gamla lösenordet gäller fortfarande.
		mockMvc.perform(get("/api/v1/auth/me").cookie(dokumenterare)).andExpect(status().isOk());
		loggaIn("dokumenterare");
	}

	@Test
	void kodenFungerarMedSetPasswordOchDetGarSedanAttLoggaIn() throws Exception {
		String kod = kod(skapa(admin, kontoId("dokumenterare")));

		mockMvc
			.perform(post("/api/v1/auth/set-password").with(csrfToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "dokumenterare", "code": "%s", "newPassword": "%s"}
						""".formatted(kod, NYTT)))
			.andExpect(status().isNoContent());

		assertThat(jdbc.sql("SELECT count(*) FROM setup_code WHERE used_at IS NOT NULL").query(Long.class).single())
			.isEqualTo(1);
		mockMvc
			.perform(post("/api/v1/auth/login").with(csrfToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "dokumenterare", "password": "%s"}
						""".formatted(NYTT)))
			.andExpect(status().isOk());
		// Efter bytet loggas den gamla sessionen ut.
		mockMvc.perform(get("/api/v1/auth/me").cookie(dokumenterare)).andExpect(status().isUnauthorized());
	}

	@Test
	void enNyKodErsatterDenForra() throws Exception {
		String gammal = kod(skapa(admin, kontoId("dokumenterare")));
		String ny = kod(skapa(admin, kontoId("dokumenterare")));

		// Mot appens klocka och inte databasens now(): koden går ut med appens klocka, och Docker-VM:ens klocka kan
		// avvika från värdens.
		assertThat(jdbc.sql("SELECT count(*) FROM setup_code WHERE valid_until > :nu")
			.param("nu", java.sql.Timestamp.from(clock.instant()))
			.query(Long.class)
			.single()).isEqualTo(1);
		mockMvc
			.perform(post("/api/v1/auth/set-password").with(csrfToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "dokumenterare", "code": "%s", "newPassword": "%s"}
						""".formatted(gammal, NYTT)))
			.andExpect(status().isBadRequest());
		mockMvc
			.perform(post("/api/v1/auth/set-password").with(csrfToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "dokumenterare", "code": "%s", "newPassword": "%s"}
						""".formatted(ny, NYTT)))
			.andExpect(status().isNoContent());
	}

	@Test
	void kodenLagrasBaraSomHash() throws Exception {
		String kod = kod(skapa(admin, kontoId("dokumenterare")));

		String rad = jdbc.sql("SELECT s::text FROM setup_code s").query(String.class).single();
		assertThat(rad).doesNotContain(kod).doesNotContain(kod.replace("-", ""));
	}

	@Test
	void skapandetLoggasUtanKoden(CapturedOutput output) throws Exception {
		int start = output.getOut().length();

		String kod = kod(skapa(admin, kontoId("dokumenterare")));
		String logg = output.getOut().substring(start);

		assertThat(logg).containsPattern("WARN .*En engångskod skapades för dokumenterare")
			.doesNotContain(kod)
			.doesNotContain(kod.replace("-", ""));
	}

	@Test
	void dokumenterareFar403OchIngenKodSkapas() throws Exception {
		skapa(dokumenterare, kontoId("dokumenterare")).andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403));

		assertThat(jdbc.sql("SELECT count(*) FROM setup_code").query(Long.class).single()).isZero();
	}

	@Test
	void anonymtAnropGer401() throws Exception {
		mockMvc.perform(post("/api/v1/accounts/{id}/setup-code", kontoId("dokumenterare")).with(csrfToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401));

		assertThat(jdbc.sql("SELECT count(*) FROM setup_code").query(Long.class).single()).isZero();
	}

	@Test
	void utanCsrfTokenGer403() throws Exception {
		mockMvc.perform(post("/api/v1/accounts/{id}/setup-code", kontoId("dokumenterare")).cookie(admin))
			.andExpect(status().isForbidden());

		assertThat(jdbc.sql("SELECT count(*) FROM setup_code").query(Long.class).single()).isZero();
	}

	@Test
	void okantKontoGer404() throws Exception {
		skapa(admin, UUID.randomUUID()).andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void ogiltigtIdGer400() throws Exception {
		mockMvc.perform(post("/api/v1/accounts/{id}/setup-code", "inte-ett-uuid").with(csrfToken()).cookie(admin))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void andraMetoderAnSkapaFarInteHamtaKoden() throws Exception {
		mockMvc.perform(get("/api/v1/accounts/{id}/setup-code", kontoId("dokumenterare")).cookie(admin))
			.andExpect(status().is4xxClientError());
	}

	// ---- Hjälpmetoder

	private ResultActions skapa(Cookie cookie, UUID kontoId) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/v1/accounts/{id}/setup-code", kontoId).with(csrfToken())
			.cookie(cookie);
		return mockMvc.perform(request);
	}

	private static String kod(ResultActions svar) throws Exception {
		String json = svar.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"code\":\"([^\"]+)\"").matcher(json);
		assertThat(matcher.find()).isTrue();
		return matcher.group(1);
	}

	private UUID kontoId(String username) {
		return jdbc.sql("SELECT id FROM user_account WHERE username = ?").param(username).query(UUID.class).single();
	}

	private int tokenversion(String username) {
		return jdbc.sql("SELECT token_version FROM user_account WHERE username = ?")
			.param(username)
			.query(Integer.class)
			.single();
	}

	private String hash(String username) {
		return jdbc.sql("SELECT password_hash FROM user_account WHERE username = ?")
			.param(username)
			.query(String.class)
			.single();
	}

	private Cookie loggaIn(String username) throws Exception {
		return mockMvc
			.perform(post("/api/v1/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content("""
					{"username": "%s", "password": "%s"}
					""".formatted(username, LOSENORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie(AccessTokens.COOKIE_NAME);
	}

}
