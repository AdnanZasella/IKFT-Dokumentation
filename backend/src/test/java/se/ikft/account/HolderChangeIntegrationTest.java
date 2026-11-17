package se.ikft.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultMatcher;

import se.ikft.TestcontainersConfiguration;
import se.ikft.member.Member;
import se.ikft.member.MemberRepository;
import se.ikft.security.AccessTokens;
import se.ikft.security.RefreshTokens;

/**
 * {@link HolderChangeService} mot en riktig Postgres: byte av innehavare för Dokumenterare-kontot.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HolderChangeIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");

	private static final UUID BERRA = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	HolderChangeService service;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	MemberRepository members;

	@BeforeEach
	void forbered() {
		stada();
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
		medlem(ANNA, "Anna", "Andersson");
		medlem(BERRA, "Bertil", "Berg");
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
	void innehavarenByterTokenversionenRaknasUppOchLosenordetBehalls() {
		String hashFore = hash("dokumenterare");

		boolean bytt = service.changeHolder(konto("dokumenterare"), medlem(ANNA));

		assertThat(bytt).isTrue();
		assertThat(holder("dokumenterare")).isEqualTo(ANNA);
		assertThat(tokenversion("dokumenterare")).isEqualTo(1);
		assertThat(hash("dokumenterare")).isEqualTo(hashFore);
		assertThat(mustChangePassword("dokumenterare")).isTrue();
	}

	@Test
	void ingenEngangskodSkapas() {
		service.changeHolder(konto("dokumenterare"), medlem(ANNA));

		assertThat(jdbc.sql("SELECT count(*) FROM setup_code").query(Long.class).single()).isZero();
	}

	@Test
	void ettAndraByteRaknarUppTokenversionenIgenOchByterInnehavare() {
		service.changeHolder(konto("dokumenterare"), medlem(ANNA));

		assertThat(service.changeHolder(konto("dokumenterare"), medlem(BERRA))).isTrue();

		assertThat(holder("dokumenterare")).isEqualTo(BERRA);
		assertThat(tokenversion("dokumenterare")).isEqualTo(2);
	}

	@Test
	void sammaInnehavareIgenAndrarIngenting() {
		service.changeHolder(konto("dokumenterare"), medlem(ANNA));
		long loggFore = antalLoggposter();

		boolean bytt = service.changeHolder(konto("dokumenterare"), medlem(ANNA));

		assertThat(bytt).isFalse();
		assertThat(tokenversion("dokumenterare")).isEqualTo(1);
		assertThat(antalLoggposter()).isEqualTo(loggFore);
	}

	@Test
	void ettForaldratKontoobjektGerInteEttOnodigtByte() {
		UserAccount foraldrat = konto("dokumenterare");
		service.changeHolder(foraldrat, medlem(ANNA));

		// Objektet säger fortfarande att kontot saknar innehavare, men databasen avgör.
		assertThat(foraldrat.getHolderMemberId()).isNull();
		assertThat(service.changeHolder(foraldrat, medlem(ANNA))).isFalse();
		assertThat(tokenversion("dokumenterare")).isEqualTo(1);
	}

	@Test
	void bytetLoggasPaKontotMedForeOchEfterVarde() {
		service.changeHolder(konto("dokumenterare"), medlem(ANNA));
		service.changeHolder(konto("dokumenterare"), medlem(BERRA));

		List<Map<String, Object>> rader = jdbc.sql("""
				SELECT a.entity_type, a.entity_id, a.action, c.field, c.before_value, c.after_value
				FROM audit_log a JOIN audit_log_change c ON c.audit_log_id = a.id
				ORDER BY a.seq
				""").query().listOfRows();
		UUID kontoId = konto("dokumenterare").getId();
		assertThat(rader).hasSize(2);
		assertThat(rader.get(0)).containsEntry("entity_type", "ACCOUNT")
			.containsEntry("entity_id", kontoId)
			.containsEntry("action", "UPDATED")
			.containsEntry("field", "holderMemberId")
			.containsEntry("before_value", null)
			.containsEntry("after_value", ANNA.toString());
		assertThat(rader.get(1)).containsEntry("before_value", ANNA.toString())
			.containsEntry("after_value", BERRA.toString());
	}

	@Test
	void adminKontotByterInteInnehavare() {
		assertThatThrownBy(() -> service.changeHolder(konto("admin"), medlem(ANNA)))
			.isInstanceOf(IllegalArgumentException.class);

		assertThat(holder("admin")).isNull();
		assertThat(tokenversion("admin")).isZero();
		assertThat(mustChangePassword("admin")).isFalse();
		assertThat(antalLoggposter()).isZero();
	}

	@Test
	void gamlaSessionerGer401MenGamlaLosenordetFungerar() throws Exception {
		MockHttpServletResponse inloggning = loggaIn("dokumenterare");
		Cookie gammalAtkomst = inloggning.getCookie(AccessTokens.COOKIE_NAME);
		Cookie gammalForneyelse = inloggning.getCookie(RefreshTokens.COOKIE_NAME);
		Cookie annanSession = loggaIn("dokumenterare").getCookie(AccessTokens.COOKIE_NAME);
		Cookie adminSession = loggaIn("admin").getCookie(AccessTokens.COOKIE_NAME);
		me(gammalAtkomst).andExpect(status().isOk());

		service.changeHolder(konto("dokumenterare"), medlem(ANNA));

		me(gammalAtkomst).andExpect(problem401());
		me(annanSession).andExpect(problem401());
		mockMvc.perform(post("/api/v1/auth/refresh").with(csrfToken()).cookie(gammalForneyelse))
			.andExpect(problem401());
		// Lösenordet gäller, så den nya innehavaren kan logga in med det som lämnades över.
		Cookie nySession = loggaIn("dokumenterare").getCookie(AccessTokens.COOKIE_NAME);
		me(nySession).andExpect(status().isOk())
			.andExpect(jsonPath("$.holderName").value("Anna Andersson"))
			.andExpect(jsonPath("$.mustChangePassword").value(true));
		// Admin berörs inte.
		me(adminSession).andExpect(status().isOk());
		assertThat(tokenversion("admin")).isZero();
	}

	// ---- Hjälpmetoder

	private UserAccount konto(String username) {
		return accounts.findByUsername(username).orElseThrow();
	}

	private Member medlem(UUID id) {
		return members.findById(id).orElseThrow();
	}

	private void medlem(UUID id, String fornamn, String efternamn) {
		jdbc.sql("""
				INSERT INTO member (id, first_name, last_name, birth_date, home_municipality)
				VALUES (:id, :fornamn, :efternamn, '1980-01-01', 'Trollhättan')
				""").param("id", id).param("fornamn", fornamn).param("efternamn", efternamn).update();
	}

	private long antalLoggposter() {
		return jdbc.sql("SELECT count(*) FROM audit_log").query(Long.class).single();
	}

	private int tokenversion(String username) {
		return jdbc.sql("SELECT token_version FROM user_account WHERE username = ?")
			.param(username)
			.query(Integer.class)
			.single();
	}

	private boolean mustChangePassword(String username) {
		return jdbc.sql("SELECT must_change_password FROM user_account WHERE username = ?")
			.param(username)
			.query(Boolean.class)
			.single();
	}

	private String hash(String username) {
		return jdbc.sql("SELECT password_hash FROM user_account WHERE username = ?")
			.param(username)
			.query(String.class)
			.single();
	}

	private UUID holder(String username) {
		return jdbc.sql("SELECT holder_member_id FROM user_account WHERE username = ?")
			.param(username)
			.query(UUID.class)
			.list()
			.getFirst();
	}

	private MockHttpServletResponse loggaIn(String username) throws Exception {
		return mockMvc
			.perform(post("/api/v1/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content("""
					{"username": "%s", "password": "%s"}
					""".formatted(username, LOSENORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse();
	}

	private ResultActions me(Cookie atkomst) throws Exception {
		return mockMvc.perform(get("/api/v1/auth/me").cookie(atkomst));
	}

	private static ResultMatcher problem401() {
		return result -> {
			status().isUnauthorized().match(result);
			content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
			jsonPath("$.status").value(401).match(result);
		};
	}

}
