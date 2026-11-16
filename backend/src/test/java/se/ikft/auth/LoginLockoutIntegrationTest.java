package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.time.Duration;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import se.ikft.TestcontainersConfiguration;
import se.ikft.security.AccessTokens;

/**
 * Spärren efter misslyckade inloggningar över HTTP. Logiken och tiderna testas i
 * {@link LoginAttemptLimiterTest} och {@link LoginServiceTest} med en styrd klocka, här kontrolleras svaret.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LoginLockoutIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	private static final String FEL_LOSENORD = "fel-losenord-helt-och-hallet";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	LoginAttemptLimiter limiter;

	@Autowired
	AuthProperties properties;

	@BeforeEach
	void forbered() {
		aterstall();
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
	}

	@AfterEach
	void aterstall() {
		limiter.clear();
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0, holder_member_id = NULL").update();
	}

	@Test
	void standardarFemForsokOchQuartonMinuter() {
		assertThat(properties.loginMaxAttempts()).isEqualTo(5);
		assertThat(properties.loginLockout()).isEqualTo(Duration.ofMinutes(15));
	}

	@Test
	void sparrenSlarTillEfterFemMisslyckadeForsokOchGer429MedRetryAfter() throws Exception {
		for (int i = 0; i < 5; i++) {
			login("dokumenterare", FEL_LOSENORD, "10.0.0.1").andExpect(status().isUnauthorized());
		}

		login("dokumenterare", FEL_LOSENORD, "10.0.0.1").andExpect(status().isTooManyRequests())
			.andExpect(retryAfterNearLockout())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.detail").value(LoginService.BLOCKED));
	}

	@Test
	void fjardeFelForsokenSparrarInte() throws Exception {
		for (int i = 0; i < 4; i++) {
			login("dokumenterare", FEL_LOSENORD, "10.0.0.1").andExpect(status().isUnauthorized());
		}

		login("dokumenterare", LOSENORD, "10.0.0.1").andExpect(status().isOk());
	}

	@Test
	void ratLosenordNekasUnderSparrenOchSattInteNagonCookie() throws Exception {
		fela("dokumenterare", "10.0.0.1", 5);

		var svar = login("dokumenterare", LOSENORD, "10.0.0.1").andExpect(status().isTooManyRequests())
			.andReturn()
			.getResponse();

		assertThat(svar.getCookie(AccessTokens.COOKIE_NAME)).isNull();
		assertThat(svar.getHeaders("Set-Cookie")).noneMatch(cookie -> cookie.contains("ikft_refresh"));
	}

	@Test
	void sparrenForAnvandarnamnGallerFranEnAnnanAdress() throws Exception {
		fela("dokumenterare", "10.0.0.1", 5);

		login("dokumenterare", LOSENORD, "10.0.0.2").andExpect(status().isTooManyRequests());
		// Ett annat konto från den andra adressen påverkas inte.
		login("admin", LOSENORD, "10.0.0.2").andExpect(status().isOk());
	}

	@Test
	void sparrenForAdressGallerAvenEttAnnatAnvandarnamn() throws Exception {
		for (String namn : new String[] { "a", "b", "c", "d", "e" }) {
			login(namn, FEL_LOSENORD, "10.0.0.1").andExpect(status().isUnauthorized());
		}

		login("admin", LOSENORD, "10.0.0.1").andExpect(status().isTooManyRequests());
		login("admin", LOSENORD, "10.0.0.2").andExpect(status().isOk());
	}

	@Test
	void okantAnvandarnamnGerSammaSvarSomEttRiktigtKonto() throws Exception {
		fela("dokumenterare", "10.0.0.1", 5);
		fela("finns-inte", "10.0.0.2", 5);

		String riktigt = login("dokumenterare", LOSENORD, "10.0.0.3").andExpect(status().isTooManyRequests())
			.andReturn()
			.getResponse()
			.getContentAsString();
		var okant = login("finns-inte", LOSENORD, "10.0.0.4").andExpect(status().isTooManyRequests())
			.andExpect(retryAfterNearLockout())
			.andReturn()
			.getResponse();

		assertThat(okant.getContentAsString()).isEqualTo(riktigt);
	}

	@Test
	void lyckadInloggningNollstallerRaknarenForAnvandarnamnet() throws Exception {
		fela("dokumenterare", "10.0.0.1", 4);
		login("dokumenterare", LOSENORD, "10.0.0.9").andExpect(status().isOk());

		fela("dokumenterare", "10.0.0.2", 4);

		login("dokumenterare", LOSENORD, "10.0.0.3").andExpect(status().isOk());
	}

	@Test
	void nulIAnvandarnamnetAvvisasInnanDetRaknasSomForsok() throws Exception {
		// Avvisas när JSON läses, före inloggningen, så det ger 400 oavsett konto och räknas inte.
		for (int i = 0; i < 6; i++) {
			login("doku\\u0000menterare", FEL_LOSENORD, "10.0.0.1").andExpect(status().isBadRequest());
		}

		login("dokumenterare", LOSENORD, "10.0.0.1").andExpect(status().isOk());
	}

	@Test
	void utgangenAtkomstcookieHindrarInteSparrensSvar() throws Exception {
		fela("dokumenterare", "10.0.0.1", 5);

		mockMvc
			.perform(post("/api/v1/auth/login").with(csrfToken())
				.with(request -> {
					request.setRemoteAddr("10.0.0.1");
					return request;
				})
				.cookie(new Cookie(AccessTokens.COOKIE_NAME, "utgangen.och.ogiltig"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "dokumenterare", "password": "%s"}
						""".formatted(LOSENORD)))
			.andExpect(status().isTooManyRequests());
	}

	private void fela(String username, String address, int antal) throws Exception {
		for (int i = 0; i < antal; i++) {
			login(username, FEL_LOSENORD, address).andExpect(status().isUnauthorized());
		}
	}

	private ResultActions login(String username, String password, String address) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").with(csrfToken()).with(request -> {
			request.setRemoteAddr(address);
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content("""
				{"username": "%s", "password": "%s"}
				""".formatted(username, password)));
	}


	/**
	 * Retry-After är de sekunder som är kvar av spärren, som högst hela tiden. En liten marginal tål att
	 * testet kör långsamt, i stället för att kräva exakt 900.
	 */
	private static ResultMatcher retryAfterNearLockout() {
		return result -> {
			String value = result.getResponse().getHeader(HttpHeaders.RETRY_AFTER);
			assertThat(value).isNotNull();
			assertThat(Long.parseLong(value)).isBetween(Duration.ofMinutes(14).toSeconds(),
					Duration.ofMinutes(15).toSeconds());
		};
	}

}
