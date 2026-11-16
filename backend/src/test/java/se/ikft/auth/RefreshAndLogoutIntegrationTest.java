package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import se.ikft.TestcontainersConfiguration;
import se.ikft.account.UserAccountRepository;
import se.ikft.security.AccessTokens;
import se.ikft.security.RefreshTokens;

/**
 * Förnyelse av token ({@code POST /api/v1/auth/refresh}) och utloggning ({@code POST /api/v1/auth/logout})
 * mot en riktig Postgres.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RefreshAndLogoutIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JwtEncoder jwtEncoder;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	Clock clock;

	@BeforeEach
	void forbered() {
		aterstall();
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
	}

	@AfterEach
	void aterstall() {
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0, holder_member_id = NULL").update();
	}

	@Test
	void inloggningenSatterForneyelsecookieMedRattAttribut() throws Exception {
		String setCookie = login("admin").getHeaders(HttpHeaders.SET_COOKIE)
			.stream()
			.filter(cookie -> cookie.startsWith(RefreshTokens.COOKIE_NAME + "="))
			.findFirst()
			.orElseThrow();

		assertThat(List.of(setCookie.split(";\\s*"))).contains("HttpOnly", "Secure", "SameSite=Strict",
				"Path=/api/v1/auth/refresh", "Max-Age=" + Duration.ofDays(30).toSeconds());
		assertThat(setCookie.toLowerCase()).doesNotContain("domain=");
	}

	@Test
	void forneyelseMedGiltigTokenGerNyAtkomsttokenSomFungerar() throws Exception {
		Cookie forneyelse = login("dokumenterare").getCookie(RefreshTokens.COOKIE_NAME);

		MockHttpServletResponse svar = refresh(forneyelse).andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("DOCUMENTER"))
			.andReturn()
			.getResponse();

		Cookie atkomst = svar.getCookie(AccessTokens.COOKIE_NAME);
		assertThat(atkomst).isNotNull();
		assertThat(atkomst.getMaxAge()).isEqualTo((int) Duration.ofMinutes(15).toSeconds());
		// Förnyelsetoken byts inte ut.
		assertThat(svar.getCookie(RefreshTokens.COOKIE_NAME)).isNull();
		mockMvc.perform(get("/api/v1/auth/me").cookie(atkomst))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("DOCUMENTER"));
	}

	@Test
	void forneyelseEfterAttTokenversionenRaknatsUppGer401() throws Exception {
		Cookie forneyelse = login("admin").getCookie(RefreshTokens.COOKIE_NAME);
		jdbc.sql("UPDATE user_account SET token_version = token_version + 1 WHERE username = 'admin'").update();

		refresh(forneyelse).andExpect(problem401());
	}

	@Test
	void utgangenForneyelsetokenGer401() throws Exception {
		Instant nu = clock.instant();
		String token = signera("admin", "refresh",nu.minus(Duration.ofDays(31)),
				nu.minus(Duration.ofSeconds(1)));

		refresh(new Cookie(RefreshTokens.COOKIE_NAME, token)).andExpect(problem401());
	}

	@Test
	void forneyelseUtanCookieGer401() throws Exception {
		refresh(null).andExpect(problem401());
	}

	@Test
	void forneyelseMedTomEllerOgiltigCookieGer401() throws Exception {
		refresh(new Cookie(RefreshTokens.COOKIE_NAME, "")).andExpect(problem401());
		refresh(new Cookie(RefreshTokens.COOKIE_NAME, "inte.en.token")).andExpect(problem401());
	}

	@Test
	void atkomsttokenKanInteAnvandasSomForneyelsetoken() throws Exception {
		Cookie atkomst = login("admin").getCookie(AccessTokens.COOKIE_NAME);

		refresh(new Cookie(RefreshTokens.COOKIE_NAME, atkomst.getValue())).andExpect(problem401());
	}

	@Test
	void forneyelsetokenKanInteAnvandasSomAtkomsttoken() throws Exception {
		Cookie forneyelse = login("admin").getCookie(RefreshTokens.COOKIE_NAME);

		mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(AccessTokens.COOKIE_NAME, forneyelse.getValue())))
			.andExpect(problem401());
	}

	@Test
	void forneyelseForKontoUtanLosenordGer401() throws Exception {
		Cookie forneyelse = login("admin").getCookie(RefreshTokens.COOKIE_NAME);
		// Samma som återställningen av lösenordet gör: lösenordet tas bort och versionen räknas upp.
		jdbc.sql("UPDATE user_account SET password_hash = NULL WHERE username = 'admin'").update();

		refresh(forneyelse).andExpect(problem401());
	}

	@Test
	void forneyelseKraverCsrfToken() throws Exception {
		Cookie forneyelse = login("admin").getCookie(RefreshTokens.COOKIE_NAME);

		mockMvc.perform(post("/api/v1/auth/refresh").cookie(forneyelse)).andExpect(status().isForbidden());
	}

	@Test
	void utgangenAtkomstcookieHindrarInteForneyelsen() throws Exception {
		Cookie forneyelse = login("admin").getCookie(RefreshTokens.COOKIE_NAME);

		mockMvc
			.perform(post("/api/v1/auth/refresh").with(csrfToken())
				.cookie(forneyelse, new Cookie(AccessTokens.COOKIE_NAME, "utgangen.och.ogiltig")))
			.andExpect(status().isOk());
	}

	@Test
	void utloggningRensarBadaCookies() throws Exception {
		Cookie atkomst = login("admin").getCookie(AccessTokens.COOKIE_NAME);

		List<String> setCookies = mockMvc.perform(post("/api/v1/auth/logout").with(csrfToken()).cookie(atkomst))
			.andExpect(status().isNoContent())
			.andReturn()
			.getResponse()
			.getHeaders(HttpHeaders.SET_COOKIE);

		String atkomstCookie = setCookies.stream()
			.filter(cookie -> cookie.startsWith(AccessTokens.COOKIE_NAME + "="))
			.findFirst()
			.orElseThrow();
		String forneyelseCookie = setCookies.stream()
			.filter(cookie -> cookie.startsWith(RefreshTokens.COOKIE_NAME + "="))
			.findFirst()
			.orElseThrow();
		assertThat(List.of(atkomstCookie.split(";\\s*"))).contains(AccessTokens.COOKIE_NAME + "=", "Max-Age=0",
				"Path=/", "HttpOnly", "Secure", "SameSite=Strict");
		// Sökvägen måste vara densamma som när cookien sattes, annars tar webbläsaren inte bort den.
		assertThat(List.of(forneyelseCookie.split(";\\s*"))).contains(RefreshTokens.COOKIE_NAME + "=", "Max-Age=0",
				"Path=/api/v1/auth/refresh", "HttpOnly", "Secure", "SameSite=Strict");
	}

	@Test
	void utloggningFungerarAvenUtanGiltigInloggning() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout").with(csrfToken())).andExpect(status().isNoContent());
		mockMvc
			.perform(post("/api/v1/auth/logout").with(csrfToken())
				.cookie(new Cookie(AccessTokens.COOKIE_NAME, "utgangen.och.ogiltig")))
			.andExpect(status().isNoContent());
	}

	@Test
	void utloggningKraverCsrfToken() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")).andExpect(status().isForbidden());
	}

	private MockHttpServletResponse login(String username) throws Exception {
		return mockMvc
			.perform(post("/api/v1/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content("""
					{"username": "%s", "password": "%s"}
					""".formatted(username, LOSENORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
	}

	private ResultActions refresh(Cookie forneyelse) throws Exception {
		var request = post("/api/v1/auth/refresh").with(csrfToken());
		if (forneyelse != null) {
			request.cookie(forneyelse);
		}
		return mockMvc.perform(request);
	}

	/** En token signerad med appens nyckel, med den angivna användningen och tider. */
	private String signera(String username, String use, Instant issuedAt, Instant expiresAt) {
		var konto = accounts.findByUsername(username).orElseThrow();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.subject(konto.getId().toString())
			.claim("token_version", konto.getTokenVersion())
			.claim("token_use", use)
			.issuedAt(issuedAt)
			.expiresAt(expiresAt)
			.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}

	private static ResultMatcher problem401() {
		return result -> {
			status().isUnauthorized().match(result);
			content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
			jsonPath("$.status").value(401).match(result);
		};
	}

}
