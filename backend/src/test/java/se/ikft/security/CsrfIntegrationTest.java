package se.ikft.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import se.ikft.TestcontainersConfiguration;

/**
 * CSRF-skyddet: anrop som ändrar data kräver token, även inloggning och set-password.
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

	private Cookie csrfForeInloggningen;

	/** Den nya CSRF-cookien från inloggningen. */
	private Cookie csrf;

	@BeforeEach
	void forbered() throws Exception {
		aterstall();
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
		csrfForeInloggningen = forstaAnropet().getCookie(CSRF_COOKIE);
		MockHttpServletResponse svar = login(csrfForeInloggningen).andExpect(status().isOk()).andReturn().getResponse();
		inloggning = svar.getCookie(AccessTokens.COOKIE_NAME);
		csrf = svar.getCookie(CSRF_COOKIE);
	}

	@AfterEach
	void aterstall() {
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0").update();
	}

	@Test
	void forstaAnropetSatterCsrfCookieSomKlientenKanLasa() throws Exception {
		// Cookien läggs till som Cookie-objekt, och MockHttpServletResponse skriver inte SameSite i huvudet.
		Cookie cookie = forstaAnropet().getCookie(CSRF_COOKIE);
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
	void inloggningenByterCsrfToken() throws Exception {
		assertThat(csrf.getValue()).isNotBlank().isNotEqualTo(csrfForeInloggningen.getValue());

		// Den gamla token gäller inte längre, även om klienten skickar med den gamla cookien.
		mockMvc
			.perform(post("/api/v1/finns-inte").cookie(inloggning, csrf)
				.header(CSRF_HEADER, csrfForeInloggningen.getValue()))
			.andExpect(status().isForbidden());
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
	void publikaSvarFarIngenCacheControlFranSakerhetskedjan() throws Exception {
		// Varje publik endpoint bestämmer själv hur länge svaret får cachas.
		assertThat(mockMvc.perform(get("/api/v1/public/finns-inte"))
			.andExpect(status().isNotFound())
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.CACHE_CONTROL)).isNull();
		// Hälsokontrollen och admin-API:et ska fortfarande inte cachas.
		assertThat(mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
		assertThat(mockMvc.perform(get("/api/v1/auth/me").cookie(inloggning))
			.andReturn()
			.getResponse()
			.getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
	}

	@Test
	void publikaApietArSkrivskyddat() throws Exception {
		// Ingen sådan endpoint finns, så 401 i stället för 404 visar att säkerhetskedjan nekade anropet.
		mockMvc.perform(post("/api/v1/public/finns-inte")).andExpect(status().isUnauthorized());
		mockMvc.perform(put("/api/v1/public/finns-inte")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/v1/public/finns-inte")).andExpect(status().isUnauthorized());
		// Inte heller med inloggning, eftersom den publika kedjan inte läser inloggningscookien.
		mockMvc.perform(post("/api/v1/public/finns-inte").cookie(inloggning, csrf).header(CSRF_HEADER, csrf.getValue()))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(head("/api/v1/public/finns-inte")).andExpect(status().isNotFound());
	}

	@Test
	void inloggningUtanCsrfTokenGer403() throws Exception {
		// Annars kan en annan webbplats logga in besökaren på ett annat konto.
		login(null).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/auth/login").cookie(csrf).contentType(MediaType.APPLICATION_JSON).content("""
				{"username": "admin", "password": "%s"}
				""".formatted(LOSENORD))).andExpect(status().isForbidden());
	}

	@Test
	void setPasswordKraverCsrfToken() throws Exception {
		String body = """
				{"username": "admin", "code": "0000-0000-0000", "newPassword": "%s"}
				""".formatted(LOSENORD);
		mockMvc.perform(post("/api/v1/auth/set-password").contentType(MediaType.APPLICATION_JSON).content(body))
			.andExpect(status().isForbidden());
		// Fel kod ger 400, inte 403, alltså godkändes anropet av CSRF-kontrollen.
		mockMvc
			.perform(post("/api/v1/auth/set-password").cookie(csrf)
				.header(CSRF_HEADER, csrf.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
	}

	/** Klientens första anrop, innan den är inloggad. Svaret är 401 men sätter CSRF-cookien. */
	private MockHttpServletResponse forstaAnropet() throws Exception {
		return mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()).andReturn().getResponse();
	}

	/** Inloggning med CSRF-cookien och token i huvudet, som klienten gör. Utan token om {@code csrf} är null. */
	private ResultActions login(Cookie csrf) throws Exception {
		MockHttpServletRequestBuilder request = post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"username": "admin", "password": "%s"}
					""".formatted(LOSENORD));
		if (csrf != null) {
			request.cookie(csrf).header(CSRF_HEADER, csrf.getValue());
		}
		return mockMvc.perform(request);
	}

}
