package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import se.ikft.TestcontainersConfiguration;
import se.ikft.security.AccessTokens;
import se.ikft.security.RefreshTokens;

/**
 * Hela flödet för Dokumenterare-kontot (#20) genom API:et, från en tom start: Admin utser en dokumenterare och
 * skapar en engångskod, dokumenteraren sätter lösenordet och loggar in, Admin byter dokumenterare, den gamla
 * sessionen nekas, och den nya innehavaren loggar in med samma lösenord, måste byta det och kan sedan använda
 * systemet som vanligt. Delstegen har egna tester, det här visar att de fungerar ihop.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DokumenterareFlodeIntegrationTest {

	private static final String ADMIN_LOSENORD = "adminens-langa-losenord";

	private static final String FORSTA = "forsta-langa-losenordet";

	private static final String NYTT = "ett-helt-nytt-losenord";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	LoginAttemptLimiter limiter;

	@Autowired
	Clock clock;

	@BeforeEach
	void forbered() {
		stada();
		jdbc.sql("UPDATE user_account SET password_hash = ? WHERE username = 'admin'")
			.param(passwordEncoder.encode(ADMIN_LOSENORD))
			.update();
	}

	@AfterEach
	void stada() {
		limiter.clear();
		jdbc.sql("DELETE FROM audit_log_change").update();
		jdbc.sql("DELETE FROM audit_log").update();
		jdbc.sql("DELETE FROM setup_code").update();
		jdbc.sql("DELETE FROM member_fifs_fee").update();
		jdbc.sql("DELETE FROM member_role").update();
		jdbc.sql("DELETE FROM documenter_assignment").update();
		jdbc.sql("""
				UPDATE user_account SET password_hash = NULL, token_version = 0, holder_member_id = NULL,
				must_change_password = false
				""").update();
		jdbc.sql("DELETE FROM member").update();
	}

	@Test
	void adminUtserDokumenterareOchByterSedanTillNyInnehavareSomKanAnvandaSystemet() throws Exception {
		// 1. Admin utser Anna och skapar en engångskod för kontots första lösenord.
		Cookie admin = login("admin", ADMIN_LOSENORD).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie(AccessTokens.COOKIE_NAME);
		UUID anna = skapaMedlem(admin, "Anna", "Andersson");
		UUID berra = skapaMedlem(admin, "Bertil", "Berg");
		utse(admin, anna).andExpect(status().isCreated());
		UUID kontoId = jdbc.sql("SELECT id FROM user_account WHERE username = 'dokumenterare'")
			.query(UUID.class)
			.single();
		String kod = JsonPath.read(mockMvc.perform(skicka(post("/api/v1/accounts/" + kontoId + "/setup-code"), admin))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.code");

		// 2. Dokumenteraren sätter lösenordet med koden och loggar in. Koden nollställer kravet på byte.
		mockMvc
			.perform(post("/api/v1/auth/set-password").with(csrfToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "dokumenterare", "code": "%s", "newPassword": "%s"}
						""".formatted(kod, FORSTA)))
			.andExpect(status().isNoContent());
		MockHttpServletResponse annasInloggning = login("dokumenterare", FORSTA).andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("DOCUMENTER"))
			.andExpect(jsonPath("$.holderName").value("Anna Andersson"))
			.andExpect(jsonPath("$.mustChangePassword").value(false))
			.andReturn()
			.getResponse();
		Cookie annasAtkomst = annasInloggning.getCookie(AccessTokens.COOKIE_NAME);
		Cookie annasForneyelse = annasInloggning.getCookie(RefreshTokens.COOKIE_NAME);
		mockMvc.perform(get("/api/v1/members").cookie(annasAtkomst)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/documenter-assignment/current").cookie(annasAtkomst))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberName").value("Anna Andersson"));

		// 3. Admin byter dokumenterare till Bertil.
		utse(admin, berra).andExpect(status().isCreated());

		// 4. Annas session nekas, både åtkomsttoken och förnyelse.
		mockMvc.perform(get("/api/v1/auth/me").cookie(annasAtkomst)).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/members").cookie(annasAtkomst)).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/v1/auth/refresh").with(csrfToken()).cookie(annasForneyelse))
			.andExpect(status().isUnauthorized());

		// 5. Bertil loggar in med samma lösenord. Han får bara se vem som är inloggad, byta lösenord och logga ut.
		Cookie berrasAtkomst = login("dokumenterare", FORSTA).andExpect(status().isOk())
			.andExpect(jsonPath("$.holderName").value("Bertil Berg"))
			.andExpect(jsonPath("$.mustChangePassword").value(true))
			.andReturn()
			.getResponse()
			.getCookie(AccessTokens.COOKIE_NAME);
		mockMvc.perform(get("/api/v1/auth/me").cookie(berrasAtkomst))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.holderName").value("Bertil Berg"))
			.andExpect(jsonPath("$.mustChangePassword").value(true));
		nekadTillsLosenordetByts(get("/api/v1/members").cookie(berrasAtkomst));
		nekadTillsLosenordetByts(get("/api/v1/documenter-assignment/current").cookie(berrasAtkomst));
		mockMvc.perform(post("/api/v1/auth/logout").with(csrfToken()).cookie(berrasAtkomst))
			.andExpect(status().isNoContent());

		// 6. Efter utloggningen gäller kravet fortfarande. Bertil loggar in igen och byter lösenord.
		Cookie igen = login("dokumenterare", FORSTA).andExpect(status().isOk())
			.andExpect(jsonPath("$.mustChangePassword").value(true))
			.andReturn()
			.getResponse()
			.getCookie(AccessTokens.COOKIE_NAME);
		Cookie efterByte = mockMvc
			.perform(post("/api/v1/auth/change-password").with(csrfToken())
				.cookie(igen)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"currentPassword": "%s", "newPassword": "%s"}
						""".formatted(FORSTA, NYTT)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mustChangePassword").value(false))
			.andReturn()
			.getResponse()
			.getCookie(AccessTokens.COOKIE_NAME);

		// 7. Nu fungerar systemet som vanligt för Bertil, och Dokumenterare får fortfarande inte göra Admins saker.
		mockMvc.perform(get("/api/v1/members").cookie(efterByte)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/documenter-assignment/current").cookie(efterByte))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberName").value("Bertil Berg"))
			.andExpect(jsonPath("$.type").value("ORDINARY"));
		mockMvc.perform(get("/api/v1/auth/me").cookie(efterByte))
			.andExpect(jsonPath("$.mustChangePassword").value(false));
		mockMvc.perform(skicka(post("/api/v1/documenter-assignment"), efterByte))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.mustChangePassword").doesNotExist());

		// 8. Det första lösenordet, som Anna kände till, gäller inte längre. Det nya gör det.
		login("dokumenterare", FORSTA).andExpect(status().isUnauthorized());
		login("dokumenterare", NYTT).andExpect(status().isOk())
			.andExpect(jsonPath("$.mustChangePassword").value(false));

		// Admin berörs inte av bytet och ser den nya dokumenteraren.
		mockMvc.perform(get("/api/v1/documenter-assignment/current").cookie(admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.memberName").value("Bertil Berg"));
		assertThat(jdbc.sql("SELECT count(*) FROM documenter_assignment WHERE ended_at IS NULL")
			.query(Long.class)
			.single()).isEqualTo(1);
	}

	// ---- Hjälpmetoder

	private ResultActions utse(Cookie admin, UUID medlem) throws Exception {
		return mockMvc.perform(skicka(post("/api/v1/documenter-assignment"), admin).content("""
				{"memberId": "%s", "startDate": "%s"}
				""".formatted(medlem, LocalDate.now(clock))));
	}

	private UUID skapaMedlem(Cookie admin, String fornamn, String efternamn) throws Exception {
		String svar = mockMvc.perform(skicka(post("/api/v1/members"), admin).content("""
				{"firstName": "%s", "lastName": "%s", "birthDate": "1980-01-01", "homeMunicipality": "Trollhättan"}
				""".formatted(fornamn, efternamn))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(svar, "$.id"));
	}

	private void nekadTillsLosenordetByts(MockHttpServletRequestBuilder request) throws Exception {
		mockMvc.perform(request)
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.mustChangePassword").value(true));
	}

	private static MockHttpServletRequestBuilder skicka(MockHttpServletRequestBuilder request, Cookie cookie) {
		return request.cookie(cookie).with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content("{}");
	}

	private ResultActions login(String username, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login").with(csrfToken())
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"username": "%s", "password": "%s"}
					""".formatted(username, password)));
	}

}
