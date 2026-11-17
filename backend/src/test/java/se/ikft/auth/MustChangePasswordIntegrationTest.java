package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.util.UUID;

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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import se.ikft.TestcontainersConfiguration;
import se.ikft.account.HolderChangeService;
import se.ikft.account.UserAccountRepository;
import se.ikft.member.MemberRepository;
import se.ikft.security.AccessTokens;
import se.ikft.security.RefreshTokens;

/**
 * Tvingat lösenordsbyte efter ett innehavarbyte (#22) mot en riktig Postgres: flaggan sätts, spärren i
 * säkerhetskedjan släpper bara igenom det som behövs för att byta lösenord, och flaggan nollställs när
 * lösenordet byts.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MustChangePasswordIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	private static final String NYTT = "ett-helt-nytt-losenord";

	private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	HolderChangeService holderChanges;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	MemberRepository members;

	@Autowired
	SetupCodeService setupCodes;

	@BeforeEach
	void forbered() {
		stada();
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
		jdbc.sql("""
				INSERT INTO member (id, first_name, last_name, birth_date, home_municipality)
				VALUES (:id, 'Anna', 'Andersson', '1980-01-01', 'Trollhättan')
				""").param("id", ANNA).update();
	}

	@AfterEach
	void stada() {
		jdbc.sql("DELETE FROM audit_log_change").update();
		jdbc.sql("DELETE FROM audit_log").update();
		jdbc.sql("DELETE FROM setup_code").update();
		jdbc.sql("""
				UPDATE user_account SET password_hash = NULL, token_version = 0, holder_member_id = NULL,
				must_change_password = false
				""").update();
		jdbc.sql("DELETE FROM member").update();
	}

	@Test
	void kontonBorjarUtanFlaggaOchInnehavarbytetSatterDen() {
		assertThat(flagga("dokumenterare")).isFalse();
		assertThat(flagga("admin")).isFalse();

		byt();

		assertThat(flagga("dokumenterare")).isTrue();
		assertThat(flagga("admin")).isFalse();
	}

	@Test
	void inloggningMedDetGamlaLosenordetFungerarOchTalarOmAttLosenordetMasteBytas() throws Exception {
		byt();

		login("dokumenterare", LOSENORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.role").value("DOCUMENTER"))
			.andExpect(jsonPath("$.holderName").value("Anna Andersson"))
			.andExpect(jsonPath("$.mustChangePassword").value(true));
	}

	@Test
	void utanFlaggaArMustChangePasswordFalseISvaren() throws Exception {
		login("dokumenterare", LOSENORD).andExpect(jsonPath("$.mustChangePassword").value(false));
		login("admin", LOSENORD).andExpect(jsonPath("$.mustChangePassword").value(false));
	}

	@Test
	void meOchLosenordsbyteArTillatnaMedFlaggan() throws Exception {
		byt();
		Cookie atkomst = cookie(login("dokumenterare", LOSENORD));

		mockMvc.perform(get("/api/v1/auth/me").cookie(atkomst))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mustChangePassword").value(true));
		changePassword(atkomst, LOSENORD, NYTT).andExpect(status().isOk());
	}

	@Test
	void allaAndraAnropNekasMed403OchMustChangePassword() throws Exception {
		byt();
		Cookie atkomst = cookie(login("dokumenterare", LOSENORD));

		// Anropen som Dokumenterare annars får göra, så att 403 beror på flaggan och inte på rollen.
		nekad(get("/api/v1/members").cookie(atkomst));
		nekad(get("/api/v1/documenter-assignment/current").cookie(atkomst));
		// Och ett som ändrar data, och en adress som inte finns, eftersom spärren gäller alla adresser.
		nekad(post("/api/v1/members").with(csrfToken())
			.cookie(atkomst)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"));
		nekad(delete("/api/v1/members/" + UUID.randomUUID()).with(csrfToken()).cookie(atkomst));
		nekad(put("/api/v1/inte-en-adress").with(csrfToken()).cookie(atkomst));
		nekad(get("/api/v1/auth/inte-me").cookie(atkomst));
	}

	@Test
	void nekadAnropGerProblemDetailSomSkiljerSigFranEttVanligt403() throws Exception {
		byt();
		Cookie atkomst = cookie(login("dokumenterare", LOSENORD));

		mockMvc.perform(get("/api/v1/members").cookie(atkomst))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403))
			.andExpect(jsonPath("$.detail").value("Lösenordet måste bytas innan du kan fortsätta."))
			.andExpect(jsonPath("$.mustChangePassword").value(true));

		// Ett vanligt 403 för fel roll har inte egenskapen.
		Cookie dokumenterareUtanFlagga = cookie(loginUtanFlagga());
		mockMvc.perform(post("/api/v1/members").with(csrfToken())
			.cookie(dokumenterareUtanFlagga)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.mustChangePassword").doesNotExist());
	}

	@Test
	void efterLosenordsbytetFungerarSystemetSomVanligt() throws Exception {
		byt();
		Cookie atkomst = cookie(login("dokumenterare", LOSENORD));

		MockHttpServletResponse svar = changePassword(atkomst, LOSENORD, NYTT).andExpect(status().isOk())
			.andExpect(jsonPath("$.mustChangePassword").value(false))
			.andReturn()
			.getResponse();

		assertThat(flagga("dokumenterare")).isFalse();
		Cookie nyAtkomst = svar.getCookie(AccessTokens.COOKIE_NAME);
		mockMvc.perform(get("/api/v1/members").cookie(nyAtkomst)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/documenter-assignment/current").cookie(nyAtkomst)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/auth/me").cookie(nyAtkomst))
			.andExpect(jsonPath("$.mustChangePassword").value(false));
		login("dokumenterare", NYTT).andExpect(jsonPath("$.mustChangePassword").value(false));
	}

	@Test
	void ettMisslyckatLosenordsbyteLamnarFlaggan() throws Exception {
		byt();
		Cookie atkomst = cookie(login("dokumenterare", LOSENORD));

		changePassword(atkomst, "fel-losenord-helt-och-hallet", NYTT).andExpect(status().isBadRequest());
		changePassword(atkomst, LOSENORD, LOSENORD).andExpect(status().isBadRequest());
		changePassword(atkomst, LOSENORD, "kort").andExpect(status().isBadRequest());

		assertThat(flagga("dokumenterare")).isTrue();
		nekad(get("/api/v1/members").cookie(atkomst));
	}

	@Test
	void utloggningOchFornyelseFungerarMedFlaggan() throws Exception {
		byt();
		MockHttpServletResponse inloggning = login("dokumenterare", LOSENORD).andReturn().getResponse();
		Cookie forneyelse = inloggning.getCookie(RefreshTokens.COOKIE_NAME);

		// Förnyelsen ger en ny token, men flaggan gäller fortfarande.
		MockHttpServletResponse fornyad = mockMvc
			.perform(post("/api/v1/auth/refresh").with(csrfToken()).cookie(forneyelse))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mustChangePassword").value(true))
			.andReturn()
			.getResponse();
		nekad(get("/api/v1/members").cookie(fornyad.getCookie(AccessTokens.COOKIE_NAME)));

		mockMvc
			.perform(post("/api/v1/auth/logout").with(csrfToken()).cookie(fornyad.getCookie(AccessTokens.COOKIE_NAME)))
			.andExpect(status().isNoContent());
	}

	@Test
	void gammalSessionFranForeInnehavarbytetGer401() throws Exception {
		Cookie gammal = cookie(login("dokumenterare", LOSENORD));

		byt();

		mockMvc.perform(get("/api/v1/auth/me").cookie(gammal)).andExpect(status().isUnauthorized());
	}

	@Test
	void sammaInnehavareIgenSatterInteFlaggan() throws Exception {
		byt();
		Cookie atkomst = cookie(login("dokumenterare", LOSENORD));
		changePassword(atkomst, LOSENORD, NYTT).andExpect(status().isOk());

		boolean bytt = holderChanges.changeHolder(accounts.findByUsername("dokumenterare").orElseThrow(),
				members.findById(ANNA).orElseThrow());

		assertThat(bytt).isFalse();
		assertThat(flagga("dokumenterare")).isFalse();
	}

	@Test
	void adminBerorsInteAvDokumenterareFlagga() throws Exception {
		byt();
		Cookie admin = cookie(login("admin", LOSENORD));

		mockMvc.perform(get("/api/v1/members").cookie(admin)).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/auth/me").cookie(admin)).andExpect(jsonPath("$.mustChangePassword").value(false));
	}

	@Test
	void engangskodNollstallerFlaggan() throws Exception {
		byt();
		String kod = setupCodes.createFor(accounts.findByUsername("dokumenterare").orElseThrow()).code();

		mockMvc
			.perform(post("/api/v1/auth/set-password").with(csrfToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "dokumenterare", "code": "%s", "newPassword": "%s"}
						""".formatted(kod, NYTT)))
			.andExpect(status().isNoContent());

		assertThat(flagga("dokumenterare")).isFalse();
		login("dokumenterare", NYTT).andExpect(jsonPath("$.mustChangePassword").value(false));
	}

	@Test
	void ettAndraInnehavarbyteSatterFlagganIgenEfterLosenordsbytet() throws Exception {
		byt();
		Cookie atkomst = cookie(login("dokumenterare", LOSENORD));
		changePassword(atkomst, LOSENORD, NYTT).andExpect(status().isOk());
		jdbc.sql("""
				INSERT INTO member (id, first_name, last_name, birth_date, home_municipality)
				VALUES ('00000000-0000-0000-0000-00000000000b', 'Bertil', 'Berg', '1980-01-01', 'Trollhättan')
				""").update();

		holderChanges.changeHolder(accounts.findByUsername("dokumenterare").orElseThrow(),
				members.findById(UUID.fromString("00000000-0000-0000-0000-00000000000b")).orElseThrow());

		assertThat(flagga("dokumenterare")).isTrue();
		login("dokumenterare", NYTT).andExpect(jsonPath("$.mustChangePassword").value(true));
	}

	// ---- Hjälpmetoder

	private void byt() {
		holderChanges.changeHolder(accounts.findByUsername("dokumenterare").orElseThrow(),
				members.findById(ANNA).orElseThrow());
	}

	private boolean flagga(String username) {
		return jdbc.sql("SELECT must_change_password FROM user_account WHERE username = ?")
			.param(username)
			.query(Boolean.class)
			.single();
	}

	private void nekad(MockHttpServletRequestBuilder request) throws Exception {
		mockMvc.perform(request)
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.mustChangePassword").value(true));
	}

	private ResultActions login(String username, String password) throws Exception {
		return mockMvc
			.perform(post("/api/v1/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content("""
					{"username": "%s", "password": "%s"}
					""".formatted(username, password)))
			.andExpect(status().isOk());
	}

	/** En Dokumenterare utan flagga, till exempel för att jämföra med ett vanligt 403. */
	private ResultActions loginUtanFlagga() throws Exception {
		jdbc.sql("UPDATE user_account SET must_change_password = false").update();
		return login("dokumenterare", LOSENORD);
	}

	private static Cookie cookie(ResultActions inloggning) {
		return inloggning.andReturn().getResponse().getCookie(AccessTokens.COOKIE_NAME);
	}

	private ResultActions changePassword(Cookie atkomst, String nuvarande, String nytt) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/change-password").with(csrfToken())
			.cookie(atkomst)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"currentPassword": "%s", "newPassword": "%s"}
					""".formatted(nuvarande, nytt)));
	}

}
