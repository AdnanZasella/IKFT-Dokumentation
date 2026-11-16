package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import se.ikft.TestcontainersConfiguration;
import se.ikft.security.AccessTokens;
import se.ikft.security.RefreshTokens;

/**
 * {@code POST /api/v1/auth/change-password} mot en riktig Postgres.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class ChangePasswordIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	private static final String NYTT = "ett-helt-nytt-losenord";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	LoginAttemptLimiter limiter;

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
	void efterBytetFungerarInteDetGamlaLosenordetMenDetNyaGor() throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		byt(atkomst, LOSENORD, NYTT).andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("DOCUMENTER"));

		loginForsok("dokumenterare", LOSENORD).andExpect(status().isUnauthorized());
		loginForsok("dokumenterare", NYTT).andExpect(status().isOk());
	}

	@Test
	void sparatLosenordArEnBcryptHashAvDetNya() throws Exception {
		Cookie atkomst = login("admin", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		byt(atkomst, LOSENORD, NYTT).andExpect(status().isOk());

		String hash = jdbc.sql("SELECT password_hash FROM user_account WHERE username = 'admin'")
			.query(String.class)
			.single();
		assertThat(hash).startsWith("$2").doesNotContain(NYTT);
		assertThat(passwordEncoder.matches(NYTT, hash)).isTrue();
	}

	@Test
	void tokenversionenRaknasUppOchDenGamlaSessionenGer401() throws Exception {
		MockHttpServletResponse inloggning = login("dokumenterare", LOSENORD);
		Cookie gammalAtkomst = inloggning.getCookie(AccessTokens.COOKIE_NAME);
		Cookie gammalForneyelse = inloggning.getCookie(RefreshTokens.COOKIE_NAME);
		// En andra session, till exempel en telefon, som ska loggas ut.
		Cookie annanSession = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		byt(gammalAtkomst, LOSENORD, NYTT).andExpect(status().isOk());

		assertThat(tokenversion("dokumenterare")).isEqualTo(1);
		me(gammalAtkomst).andExpect(problem401());
		me(annanSession).andExpect(problem401());
		refresh(gammalForneyelse).andExpect(problem401());
	}

	@Test
	void denSomByttFarNyaCookiesOchForblirInloggad() throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		MockHttpServletResponse svar = byt(atkomst, LOSENORD, NYTT).andExpect(status().isOk())
			.andReturn()
			.getResponse();

		Cookie nyAtkomst = svar.getCookie(AccessTokens.COOKIE_NAME);
		Cookie nyForneyelse = svar.getCookie(RefreshTokens.COOKIE_NAME);
		assertThat(nyAtkomst).isNotNull();
		assertThat(nyForneyelse).isNotNull();
		assertThat(svar.getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(c -> c.startsWith("__Host-XSRF-TOKEN="));
		me(nyAtkomst).andExpect(status().isOk());
		// Den nya förnyelsetoken gäller också.
		refresh(nyForneyelse).andExpect(status().isOk());
	}

	@Test
	void felNuvarandeLosenordGer400OchAndrarInget() throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);
		String hashFore = hash("dokumenterare");

		byt(atkomst, "fel-losenord-helt-och-hallet", NYTT).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(PasswordChangeService.WRONG_CURRENT));

		assertThat(hash("dokumenterare")).isEqualTo(hashFore);
		assertThat(tokenversion("dokumenterare")).isZero();
		// Sessionen gäller fortfarande.
		me(atkomst).andExpect(status().isOk());
	}

	@Test
	void nuvarandeLosenordSomBryterMotReglernaGer400() throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		byt(atkomst, "å".repeat(37), NYTT).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(PasswordChangeService.WRONG_CURRENT));
		byt(atkomst, "x".repeat(257), NYTT).andExpect(status().isBadRequest());
	}

	@Test
	void forKortNyttLosenordGer400MedFaltfel() throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		byt(atkomst, LOSENORD, "kort").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("newPassword"))
			.andExpect(jsonPath("$.errors[0].message").value("Lösenordet måste ha minst 12 tecken."));

		assertThat(tokenversion("dokumenterare")).isZero();
	}

	@Test
	void forLangtNyttLosenordGer400() throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		// 37 tecken med två byte vardera är 74 byte, över BCrypts gräns på 72.
		byt(atkomst, LOSENORD, "å".repeat(37)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("newPassword"));
	}

	@Test
	void nyttLosenordGransvardenTolvTecken() throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		byt(atkomst, LOSENORD, "elvatecken-").andExpect(status().isBadRequest());
		byt(atkomst, LOSENORD, "tolvtecken-x").andExpect(status().isOk());
	}

	@Test
	void tommaOchSaknadeFaltGer400() throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		postaRaa(atkomst, """
				{"currentPassword": "", "newPassword": "%s"}
				""".formatted(NYTT)).andExpect(status().isBadRequest());
		postaRaa(atkomst, """
				{"currentPassword": "%s", "newPassword": "   "}
				""".formatted(LOSENORD)).andExpect(status().isBadRequest());
		postaRaa(atkomst, "{}").andExpect(status().isBadRequest());
	}

	@Test
	void sammaLosenordSomFornGer400() throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		byt(atkomst, LOSENORD, LOSENORD).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(PasswordChangeService.SAME_PASSWORD));

		assertThat(tokenversion("dokumenterare")).isZero();
	}

	@Test
	void anonymtAnropGer401() throws Exception {
		mockMvc
			.perform(post("/api/v1/auth/change-password").with(csrfToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"currentPassword": "%s", "newPassword": "%s"}
						""".formatted(LOSENORD, NYTT)))
			.andExpect(problem401());

		assertThat(tokenversion("dokumenterare")).isZero();
	}

	@Test
	void utanCsrfTokenGer403() throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		mockMvc
			.perform(post("/api/v1/auth/change-password").cookie(atkomst)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"currentPassword": "%s", "newPassword": "%s"}
						""".formatted(LOSENORD, NYTT)))
			.andExpect(status().isForbidden());
	}

	@Test
	void bytetBerorBaraDetEgnaKontot() throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);
		Cookie adminAtkomst = login("admin", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		byt(atkomst, LOSENORD, NYTT).andExpect(status().isOk());

		assertThat(tokenversion("admin")).isZero();
		me(adminAtkomst).andExpect(status().isOk());
		loginForsok("admin", LOSENORD).andExpect(status().isOk());
	}

	@Test
	void femFelNuvarandeLosenordSparrarFortsattGissning() throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);
		for (int i = 0; i < 5; i++) {
			byt(atkomst, "fel-losenord-helt-och-hallet", NYTT).andExpect(status().isBadRequest());
		}

		// Inte ens rätt lösenord släpps in under spärren.
		byt(atkomst, LOSENORD, NYTT).andExpect(status().isTooManyRequests());

		assertThat(tokenversion("dokumenterare")).isZero();
	}

	@Test
	void bytetLoggasUtanLosenordenOchEfterCommit(CapturedOutput output) throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		byt(atkomst, LOSENORD, NYTT).andExpect(status().isOk());

		assertThat(output.getOut()).contains("Lösenordet för dokumenterare byttes av kontots innehavare.");
		assertThat(output.getOut()).doesNotContain(LOSENORD).doesNotContain(NYTT);
	}

	@Test
	void misslyckatByteLoggasInteSomLyckat(CapturedOutput output) throws Exception {
		Cookie atkomst = login("dokumenterare", LOSENORD).getCookie(AccessTokens.COOKIE_NAME);

		byt(atkomst, "fel-losenord-helt-och-hallet", NYTT).andExpect(status().isBadRequest());

		assertThat(output.getOut()).doesNotContain("byttes av kontots innehavare");
		assertThat(output.getOut()).contains("Byte av lösenord misslyckades för dokumenterare");
	}

	private ResultActions byt(Cookie atkomst, String nuvarande, String nytt) throws Exception {
		return postaRaa(atkomst, """
				{"currentPassword": "%s", "newPassword": "%s"}
				""".formatted(nuvarande, nytt));
	}

	private ResultActions postaRaa(Cookie atkomst, String json) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/change-password").with(csrfToken())
			.cookie(atkomst)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private MockHttpServletResponse login(String username, String password) throws Exception {
		return loginForsok(username, password).andExpect(status().isOk()).andReturn().getResponse();
	}

	private ResultActions loginForsok(String username, String password) throws Exception {
		return mockMvc
			.perform(post("/api/v1/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content("""
					{"username": "%s", "password": "%s"}
					""".formatted(username, password)));
	}

	private ResultActions me(Cookie atkomst) throws Exception {
		return mockMvc.perform(get("/api/v1/auth/me").cookie(atkomst));
	}

	private ResultActions refresh(Cookie forneyelse) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/refresh").with(csrfToken()).cookie(forneyelse));
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

	private static ResultMatcher problem401() {
		return result -> {
			status().isUnauthorized().match(result);
			content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
			jsonPath("$.status").value(401).match(result);
		};
	}

}
