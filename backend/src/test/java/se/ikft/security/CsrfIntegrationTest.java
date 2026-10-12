package se.ikft.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


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
import org.springframework.test.web.servlet.MockMvc;

import se.ikft.TestcontainersConfiguration;

/**
 * CSRF-skyddet: anrop som ändrar data kräver token, utom inloggning och set-password.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CsrfIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	private static final String CSRF_COOKIE = SecurityConfig.CSRF_COOKIE_NAME;

	private static final String CSRF_HEADER = "X-XSRF-TOKEN";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	private Cookie inloggning;

	private Cookie csrf;

	@BeforeEach
	void forbered() throws Exception {
		aterstall();
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
		MockHttpServletResponse svar = login();
		inloggning = svar.getCookie(AccessTokens.COOKIE_NAME);
		csrf = svar.getCookie(CSRF_COOKIE);
	}

	@AfterEach
	void aterstall() {
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0").update();
	}

	@Test
	void inloggningenSatterCsrfCookieSomKlientenKanLasa() throws Exception {
		MockHttpServletResponse svar = login();
		// Båda cookies ska finnas kvar, inte bara den som sattes sist.
		assertThat(svar.getHeaders(HttpHeaders.SET_COOKIE)).anyMatch(c -> c.startsWith(CSRF_COOKIE + "="))
			.anyMatch(c -> c.startsWith(AccessTokens.COOKIE_NAME + "="));

		// Cookien läggs till som Cookie-objekt, och MockHttpServletResponse skriver inte SameSite i huvudet.
		Cookie cookie = svar.getCookie(CSRF_COOKIE);
		assertThat(cookie.isHttpOnly()).isFalse();
		assertThat(cookie.getSecure()).isTrue();
		assertThat(cookie.getPath()).isEqualTo("/");
		assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
	}

	@Test
	void postUtanCsrfTokenGer403SomProblemDetail() throws Exception {
		mockMvc.perform(post("/api/v1/finns-inte").cookie(inloggning, csrf))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void postMedFelCsrfTokenGer403() throws Exception {
		mockMvc.perform(post("/api/v1/finns-inte").cookie(inloggning, csrf).header(CSRF_HEADER, "fel"))
			.andExpect(status().isForbidden());
	}

	@Test
	void postMedCsrfTokenSlappsIgenom() throws Exception {
		// Ingen sådan endpoint finns, så 404 visar att CSRF-kontrollen godkände anropet.
		mockMvc.perform(post("/api/v1/finns-inte").cookie(inloggning, csrf).header(CSRF_HEADER, csrf.getValue()))
			.andExpect(status().isNotFound());
	}

	@Test
	void getKraverIngenCsrfToken() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me").cookie(inloggning)).andExpect(status().isOk());
	}

	@Test
	void publikaSvarFarIngenCookie() throws Exception {
		// En cookie i svaret hindrar att det cachas.
		assertThat(mockMvc.perform(get("/actuator/health").cookie(inloggning, csrf))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
		assertThat(mockMvc.perform(get("/api/v1/public/finns-inte"))
			.andReturn()
			.getResponse()
			.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
	}

	@Test
	void setPasswordKraverIngenCsrfToken() throws Exception {
		// Fel kod ger 400, inte 403, alltså godkändes anropet av CSRF-kontrollen.
		mockMvc.perform(post("/api/v1/auth/set-password").contentType(MediaType.APPLICATION_JSON).content("""
				{"username": "admin", "code": "0000-0000-0000", "newPassword": "%s"}
				""".formatted(LOSENORD))).andExpect(status().isBadRequest());
	}

	@Test
	void inloggningSomInteArJsonKraverCsrfToken() throws Exception {
		// Ett formulär från en annan webbplats kan skickas utan CORS och ska inte kunna logga in besökaren.
		for (MediaType typ : new MediaType[] { MediaType.APPLICATION_FORM_URLENCODED, MediaType.TEXT_PLAIN }) {
			mockMvc.perform(post("/api/v1/auth/login").contentType(typ).content("""
					{"username": "admin", "password": "%s"}
					""".formatted(LOSENORD))).andExpect(status().isForbidden());
		}
		mockMvc.perform(post("/api/v1/auth/set-password").contentType(MediaType.TEXT_PLAIN).content("""
				{"username": "admin", "code": "0000-0000-0000", "newPassword": "%s"}
				""".formatted(LOSENORD))).andExpect(status().isForbidden());
	}

	@Test
	void inloggningMedJsonOchTeckenkodningKraverIngenCsrfToken() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType("application/json;charset=UTF-8").content("""
				{"username": "admin", "password": "%s"}
				""".formatted(LOSENORD))).andExpect(status().isOk());
	}

	private MockHttpServletResponse login() throws Exception {
		// Ingen CSRF-token skickas med.
		return mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
					{"username": "admin", "password": "%s"}
					""".formatted(LOSENORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
	}

}
