package se.ikft.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.text.Normalizer;
import java.time.Duration;
import java.util.List;

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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import se.ikft.TestcontainersConfiguration;
import se.ikft.account.UserAccountRepository;
import se.ikft.security.AccessTokens;

/**
 * {@code POST /api/v1/auth/login} mot en riktig Postgres.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LoginIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JwtDecoder jwtDecoder;

	@Autowired
	UserAccountRepository accounts;

	/** Svaret på ett fel lösenord. Alla andra inloggningsfel ska ge exakt samma svar. */
	private String felLosenordSvar;

	@BeforeEach
	void forbered() throws Exception {
		aterstall();
		sattLosenord("admin", LOSENORD);
		sattLosenord("dokumenterare", LOSENORD);
		felLosenordSvar = felsvar("dokumenterare", "fel-losenord-helt-och-hallet");
	}

	@AfterEach
	void aterstall() {
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0, holder_member_id = NULL").update();
		jdbc.sql("DELETE FROM member").update();
	}

	@Test
	void lyckadInloggningSatterCookieMedRattAttribut() throws Exception {
		String setCookie = login("dokumenterare", LOSENORD).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(cookie -> cookie.startsWith(AccessTokens.COOKIE_NAME + "="))
			.findFirst()
			.orElseThrow();

		List<String> attribut = List.of(setCookie.split(";\\s*"));
		assertThat(attribut).contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/",
				"Max-Age=" + Duration.ofMinutes(15).toSeconds());
		assertThat(attribut).noneMatch(a -> a.toLowerCase().startsWith("domain="));
	}

	@Test
	void tokenInnehallerKontoIdOchTokenversionMenIngenRoll() throws Exception {
		jdbc.sql("UPDATE user_account SET token_version = 3 WHERE username = 'admin'").update();

		Jwt jwt = jwtDecoder.decode(token(login("admin", LOSENORD).andExpect(status().isOk())));

		assertThat(jwt.getSubject())
			.isEqualTo(accounts.findByUsername("admin").orElseThrow().getId().toString());
		// Rollen läses från databasen vid varje anrop, så den ska inte finnas i token.
		assertThat(jwt.hasClaim("role")).isFalse();
		assertThat(((Number) jwt.getClaim("token_version")).intValue()).isEqualTo(3);
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
	}

	@Test
	void svaretInnehallerRollOchInnehavarensNamn() throws Exception {
		jdbc.sql("""
				INSERT INTO member (id, first_name, last_name, birth_date, home_municipality)
				VALUES ('00000000-0000-0000-0000-000000000001', 'Testa', 'Testsson', '1990-01-01', 'Trollhättan')
				""").update();
		jdbc.sql("""
				UPDATE user_account SET holder_member_id = '00000000-0000-0000-0000-000000000001'
				WHERE username = 'dokumenterare'
				""").update();

		login("dokumenterare", LOSENORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("DOCUMENTER"))
			.andExpect(jsonPath("$.holderName").value("Testa Testsson"));
	}

	@Test
	void kontoUtanInnehavareGerHolderNameNull() throws Exception {
		login("admin", LOSENORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("ADMIN"))
			.andExpect(jsonPath("$.holderName").isEmpty());
	}

	@Test
	void felLosenordGer401SomProblemDetailUtanCookie() throws Exception {
		login("dokumenterare", "fel-losenord-helt-och-hallet").andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(LoginService.INVALID_CREDENTIALS))
			.andExpect(result -> assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
				.noneMatch(cookie -> cookie.startsWith(AccessTokens.COOKIE_NAME + "=")));
	}

	@Test
	void okantAnvandarnamnGerSammaSvarSomFelLosenord() throws Exception {
		assertThat(felsvar("finns-inte", LOSENORD)).isEqualTo(felLosenordSvar);
	}

	@Test
	void kontoUtanLosenordGerSammaSvarSomFelLosenord() throws Exception {
		jdbc.sql("UPDATE user_account SET password_hash = NULL WHERE username = 'dokumenterare'").update();

		assertThat(felsvar("dokumenterare", LOSENORD)).isEqualTo(felLosenordSvar);
	}

	@Test
	void forLangtLosenordGerSammaSvarSomFelLosenord() throws Exception {
		// 37 × 2 byte = 74 byte, över BCrypts gräns på 72.
		assertThat(felsvar("dokumenterare", "å".repeat(37))).isEqualTo(felLosenordSvar);
	}

	@Test
	void anvandarnamnetJamforsExakt() throws Exception {
		assertThat(felsvar("Dokumenterare", LOSENORD)).isEqualTo(felLosenordSvar);
	}

	@Test
	void losenordetNormaliserasTillNfc() throws Exception {
		String nfc = "Trollhättan-moskén";
		sattLosenord("dokumenterare", nfc);

		login("dokumenterare", Normalizer.normalize(nfc, Normalizer.Form.NFD)).andExpect(status().isOk());
	}

	@Test
	void tommaOchForLangaFaltGerSammaSvarSomFelLosenord() throws Exception {
		assertThat(felsvar("", LOSENORD)).isEqualTo(felLosenordSvar);
		assertThat(felsvar("   ", LOSENORD)).isEqualTo(felLosenordSvar);
		assertThat(felsvar("d".repeat(51), LOSENORD)).isEqualTo(felLosenordSvar);
		// NUL avvisas av databasen och får inte ge ett annat fel.
		assertThat(felsvar("doku\\u0000menterare", LOSENORD)).isEqualTo(felLosenordSvar);
		assertThat(felsvar("dokumenterare", "")).isEqualTo(felLosenordSvar);
		assertThat(felsvar("dokumenterare", "x".repeat(257))).isEqualTo(felLosenordSvar);
	}

	@Test
	void losenordMedBaraMellanslagKanAnvandas() throws Exception {
		sattLosenord("dokumenterare", " ".repeat(12));

		login("dokumenterare", " ".repeat(12)).andExpect(status().isOk());
	}

	@Test
	void saknadeFaltGer400() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.length()").value(2));
	}

	@Test
	void inloggningarLoggasUtanLosenord(CapturedOutput output) throws Exception {
		int start = output.getOut().length();
		login("dokumenterare", LOSENORD).andExpect(status().isOk());
		login("dokumenterare", "fel-losenord-helt-och-hallet").andExpect(status().isUnauthorized());
		login("finns-inte\nFALSK LOGGRAD", LOSENORD).andExpect(status().isUnauthorized());
		String logg = output.getOut().substring(start);

		assertThat(logg).containsPattern("INFO .*Inloggning lyckades för dokumenterare")
			.containsPattern("WARN .*Inloggning misslyckades för dokumenterare")
			.containsPattern("WARN .*Inloggning misslyckades för ett okänt användarnamn")
			.doesNotContain(LOSENORD)
			.doesNotContain("fel-losenord-helt-och-hallet")
			.doesNotContain("FALSK LOGGRAD");
	}

	private void sattLosenord(String username, String password) {
		jdbc.sql("UPDATE user_account SET password_hash = ? WHERE username = ?")
			.params(passwordEncoder.encode(password), username)
			.update();
	}

	private ResultActions login(String username, String password) throws Exception {
		String body = """
				{"username": "%s", "password": "%s"}
				""".formatted(username.replace("\n", "\\n"), password);
		return mockMvc.perform(post("/api/v1/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String felsvar(String username, String password) throws Exception {
		return login(username, password).andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString(UTF_8);
	}

	private static String token(ResultActions result) {
		return result.andReturn().getResponse().getCookie(AccessTokens.COOKIE_NAME).getValue();
	}

}
