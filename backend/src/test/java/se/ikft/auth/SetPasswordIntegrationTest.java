package se.ikft.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import se.ikft.TestcontainersConfiguration;
import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;

/**
 * {@code POST /api/v1/auth/set-password} mot en riktig Postgres.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SetPasswordIntegrationTest {

	private static final String NYTT_LOSENORD = "ett-langt-losenord";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	SetupCodeService setupCodes;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	PasswordEncoder passwordEncoder;

	/** Svaret på en felaktig kod. Alla andra fel med koden ska ge exakt samma svar. */
	private String felKodSvar;

	@BeforeEach
	void forbered() throws Exception {
		aterstall();
		felKodSvar = felsvar("dokumenterare", "0000-0000-0000");
	}

	@AfterEach
	void aterstall() {
		jdbc.sql("DELETE FROM setup_code").update();
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0").update();
	}

	@Test
	void giltigKodSatterLosenordetSomBcryptOchRaknarUppTokenversionen() throws Exception {
		String kod = nyKod("dokumenterare");

		setPassword("dokumenterare", kod, NYTT_LOSENORD).andExpect(status().isNoContent());

		Map<String, Object> konto = jdbc
			.sql("SELECT password_hash, token_version FROM user_account WHERE username = 'dokumenterare'")
			.query()
			.singleRow();
		String hash = (String) konto.get("password_hash");
		assertThat(hash).startsWith("$2").doesNotContain(NYTT_LOSENORD);
		assertThat(passwordEncoder.matches(NYTT_LOSENORD, hash)).isTrue();
		assertThat(konto.get("token_version")).isEqualTo(1);
		assertThat(jdbc.sql("SELECT count(*) FROM setup_code WHERE used_at IS NOT NULL").query(Long.class).single())
			.isEqualTo(1);
	}

	@Test
	void kodenFarSkrivasMedSmaBokstaverOchUtanBindestreck() throws Exception {
		String kod = nyKod("dokumenterare").replace("-", "").toLowerCase();

		setPassword("dokumenterare", kod, NYTT_LOSENORD).andExpect(status().isNoContent());
	}

	@Test
	void felaktigKodGer400SomProblemDetail() throws Exception {
		nyKod("dokumenterare");

		setPassword("dokumenterare", "0000-0000-0000", NYTT_LOSENORD).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(SetupCodeService.INVALID_CODE));
		assertThat(losenordshash("dokumenterare")).isNull();
	}

	@Test
	void utgangenKodGerSammaSvarSomFelaktigKod() throws Exception {
		String kod = nyKod("dokumenterare");
		jdbc.sql("UPDATE setup_code SET valid_until = now() - interval '1 minute'").update();

		assertThat(felsvar("dokumenterare", kod)).isEqualTo(felKodSvar);
		assertThat(losenordshash("dokumenterare")).isNull();
	}

	@Test
	void redanAnvandKodGerSammaSvarSomFelaktigKod() throws Exception {
		String kod = nyKod("dokumenterare");
		setPassword("dokumenterare", kod, NYTT_LOSENORD).andExpect(status().isNoContent());
		String hashEfterForstaGangen = losenordshash("dokumenterare");

		assertThat(felsvar("dokumenterare", kod)).isEqualTo(felKodSvar);
		assertThat(losenordshash("dokumenterare")).isEqualTo(hashEfterForstaGangen);
	}

	@Test
	void ersattKodGerSammaSvarSomFelaktigKod() throws Exception {
		String gammal = nyKod("dokumenterare");
		String ny = nyKod("dokumenterare");

		assertThat(felsvar("dokumenterare", gammal)).isEqualTo(felKodSvar);
		setPassword("dokumenterare", ny, NYTT_LOSENORD).andExpect(status().isNoContent());
	}

	@Test
	void okantAnvandarnamnGerSammaSvarSomFelaktigKod() throws Exception {
		String kod = nyKod("dokumenterare");

		assertThat(felsvar("finns-inte", kod)).isEqualTo(felKodSvar);
	}

	@Test
	void kodForEttAnnatKontoGerSammaSvarSomFelaktigKod() throws Exception {
		String adminKod = nyKod("admin");

		assertThat(felsvar("dokumenterare", adminKod)).isEqualTo(felKodSvar);
		assertThat(losenordshash("dokumenterare")).isNull();
	}

	@Test
	void kodenLagrasAldrigIKlartext() throws Exception {
		String kod = nyKod("dokumenterare");
		String utanBindestreck = kod.replace("-", "");

		String rad = jdbc.sql("SELECT s::text FROM setup_code s").query(String.class).single();
		assertThat(rad).doesNotContain(kod).doesNotContain(utanBindestreck);

		String sha256 = HexFormat.of()
			.formatHex(MessageDigest.getInstance("SHA-256").digest(utanBindestreck.getBytes(UTF_8)));
		assertThat(jdbc.sql("SELECT code_hash FROM setup_code").query(String.class).single()).isEqualTo(sha256);
	}

	@Test
	void forKortLosenordGer400OchForbrukarInteKoden() throws Exception {
		String kod = nyKod("dokumenterare");

		setPassword("dokumenterare", kod, "a".repeat(11)).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Lösenordet måste ha minst 12 tecken."));
		assertThat(losenordshash("dokumenterare")).isNull();

		setPassword("dokumenterare", kod, "a".repeat(12)).andExpect(status().isNoContent());
	}

	@Test
	void forLangtLosenordGer400() throws Exception {
		String kod = nyKod("dokumenterare");

		// 37 × 2 byte = 74 byte, över BCrypts gräns på 72.
		setPassword("dokumenterare", kod, "å".repeat(37)).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
		assertThat(losenordshash("dokumenterare")).isNull();
	}

	@Test
	void tommaFaltGer400MedFaltlista() throws Exception {
		setPassword("", "", "").andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.length()").value(3));
	}

	private String nyKod(String username) {
		UserAccount konto = accounts.findByUsername(username).orElseThrow();
		return setupCodes.createFor(konto).code();
	}

	private ResultActions setPassword(String username, String code, String newPassword) throws Exception {
		String body = """
				{"username": "%s", "code": "%s", "newPassword": "%s"}
				""".formatted(username, code, newPassword);
		return mockMvc
			.perform(post("/api/v1/auth/set-password").contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private String felsvar(String username, String code) throws Exception {
		return setPassword(username, code, NYTT_LOSENORD).andExpect(status().isBadRequest())
			.andReturn()
			.getResponse()
			.getContentAsString(UTF_8);
	}

	private String losenordshash(String username) {
		// list() i stället för single(), som inte tillåter null.
		return jdbc.sql("SELECT password_hash FROM user_account WHERE username = ?")
			.param(username)
			.query(String.class)
			.list()
			.getFirst();
	}

}
