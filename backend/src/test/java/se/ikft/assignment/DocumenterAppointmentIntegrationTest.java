package se.ikft.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.time.Clock;
import java.time.LocalDate;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import se.ikft.TestcontainersConfiguration;
import se.ikft.security.AccessTokens;

/**
 * {@code POST /api/v1/documenter-assignment} mot en riktig Postgres. Datumen räknas från dagens datum enligt den
 * injicerade klockan, eftersom testkontexten har den riktiga klockan.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DocumenterAppointmentIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	private static final UUID ANNA = UUID.fromString("00000000-0000-0000-0000-00000000000a");

	private static final UUID BERRA = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	private static final UUID INAKTIV = UUID.fromString("00000000-0000-0000-0000-00000000000c");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	Clock clock;

	Cookie admin;

	Cookie dokumenterare;

	LocalDate idag;

	@BeforeEach
	void forbered() throws Exception {
		stada();
		idag = LocalDate.now(clock);
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
		medlem(ANNA, "Anna", "Andersson", "ACTIVE");
		medlem(BERRA, "Bertil", "Berg", "ACTIVE");
		medlem(INAKTIV, "Ines", "Inaktiv", "INACTIVE");
		admin = loggaIn("admin");
		dokumenterare = loggaIn("dokumenterare");
	}

	@AfterEach
	void stada() {
		jdbc.sql("DELETE FROM audit_log_change").update();
		jdbc.sql("DELETE FROM audit_log").update();
		jdbc.sql("DELETE FROM documenter_assignment").update();
		jdbc.sql("""
				UPDATE user_account SET password_hash = NULL, token_version = 0, holder_member_id = NULL,
				must_change_password = false
				""").update();
		jdbc.sql("DELETE FROM member").update();
	}

	// ---- Börjar i dag

	@Test
	void uppdragSomBorjarIDagGer201OchByterInnehavareDirekt() throws Exception {
		utse(admin, ANNA, idag).andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").isString())
			.andExpect(jsonPath("$.memberId").value(ANNA.toString()))
			.andExpect(jsonPath("$.memberName").value("Anna Andersson"))
			.andExpect(jsonPath("$.type").value("ORDINARY"))
			.andExpect(jsonPath("$.startDate").value(idag.toString()))
			.andExpect(jsonPath("$.endDate").value(ultimo(idag).toString()));

		assertThat(holder()).isEqualTo(ANNA);
		assertThat(tokenversion()).isEqualTo(1);
		assertThat(antalUppdrag()).isEqualTo(1);
		current(admin).andExpect(jsonPath("$.memberId").value(ANNA.toString()))
			.andExpect(jsonPath("$.type").value("ORDINARY"));
	}

	@Test
	void gamlaSessionenArOgiltigOchLosenordetOforandrat() throws Exception {
		String hashFore = hash();

		utse(admin, ANNA, idag).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/auth/me").cookie(dokumenterare)).andExpect(status().isUnauthorized());
		assertThat(hash()).isEqualTo(hashFore);
		// Det gamla lösenordet fungerar, men den nya innehavaren måste byta det.
		mockMvc
			.perform(post("/api/v1/auth/login").with(csrfToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "dokumenterare", "password": "%s"}
						""".formatted(LOSENORD)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.holderName").value("Anna Andersson"))
			.andExpect(jsonPath("$.mustChangePassword").value(true));
	}

	@Test
	void ettNyttUppdragIDagAvslutarDetPagaendeOrdinarie() throws Exception {
		utse(admin, ANNA, idag).andExpect(status().isCreated());

		utse(admin, BERRA, idag).andExpect(status().isCreated());

		assertThat(holder()).isEqualTo(BERRA);
		assertThat(tokenversion()).isEqualTo(2);
		assertThat(antalUppdrag()).isEqualTo(2);
		assertThat(avslutade(ANNA)).isTrue();
		assertThat(avslutade(BERRA)).isFalse();
		current(admin).andExpect(jsonPath("$.memberId").value(BERRA.toString()));
	}

	@Test
	void sammaMedlemIgenGer200UtanNyttUppdragOchUtanNyTokenversion() throws Exception {
		utse(admin, ANNA, idag).andExpect(status().isCreated());
		assertThat(tokenversion()).isEqualTo(1);
		Cookie innehavare = loggaIn("dokumenterare");

		utse(admin, ANNA, idag).andExpect(status().isOk())
			.andExpect(jsonPath("$.memberId").value(ANNA.toString()))
			.andExpect(jsonPath("$.startDate").value(idag.toString()));

		assertThat(tokenversion()).isEqualTo(1);
		assertThat(antalUppdrag()).isEqualTo(1);
		assertThat(avslutade(ANNA)).isFalse();
		// Sessionen för den som redan är innehavare loggas inte ut.
		mockMvc.perform(get("/api/v1/auth/me").cookie(innehavare)).andExpect(status().isOk());
	}

	@Test
	void sammaMedlemMedEttSenareStartdatumSomRedanGallerGer200() throws Exception {
		LocalDate nastaAr = idag.plusYears(1).withDayOfYear(1);
		utse(admin, ANNA, nastaAr).andExpect(status().isCreated());

		// Annas uppdrag gäller redan 1 juli nästa år, så inget nytt skapas.
		utse(admin, ANNA, nastaAr.plusMonths(6)).andExpect(status().isOk())
			.andExpect(jsonPath("$.startDate").value(nastaAr.toString()));

		assertThat(antalUppdrag()).isEqualTo(1);
		assertThat(avslutade(ANNA)).isFalse();
	}

	@Test
	void ettAktivtTillfalligtUppdragGarForeOchInnehavarenByterInte() throws Exception {
		insattUppdrag(BERRA, "TEMPORARY", idag.minusDays(1), idag.plusDays(5));
		jdbc.sql("UPDATE user_account SET holder_member_id = ? WHERE username = 'dokumenterare'")
			.param(BERRA)
			.update();

		utse(admin, ANNA, idag).andExpect(status().isCreated());

		assertThat(holder()).isEqualTo(BERRA);
		assertThat(tokenversion()).isZero();
		assertThat(antalUppdrag()).isEqualTo(2);
		current(admin).andExpect(jsonPath("$.memberId").value(BERRA.toString()))
			.andExpect(jsonPath("$.type").value("TEMPORARY"));
	}

	// ---- Börjar senare

	@Test
	void uppdragForNastaArByterInteInnehavareOchLamnarDetPagaendeOrdinarie() throws Exception {
		utse(admin, ANNA, idag).andExpect(status().isCreated());
		LocalDate nastaAr = idag.plusYears(1).withDayOfYear(1);

		utse(admin, BERRA, nastaAr).andExpect(status().isCreated())
			.andExpect(jsonPath("$.startDate").value(nastaAr.toString()))
			.andExpect(jsonPath("$.endDate").value(ultimo(nastaAr).toString()));

		assertThat(holder()).isEqualTo(ANNA);
		assertThat(tokenversion()).isEqualTo(1);
		// Annas uppdrag gäller året ut, Berras börjar först 1 januari.
		current(admin).andExpect(jsonPath("$.memberId").value(ANNA.toString()))
			.andExpect(jsonPath("$.endDate").value(ultimo(idag).toString()));
	}

	@Test
	void nextYearAssignedBlirSantNarEnDokumenterareUtsesForNastaAr() throws Exception {
		current(admin).andExpect(jsonPath("$.nextYearAssigned").value(false));

		utse(admin, ANNA, idag.plusYears(1).withDayOfYear(1)).andExpect(status().isCreated());

		current(admin).andExpect(jsonPath("$.nextYearAssigned").value(true)).andExpect(jsonPath("$.reminder").value(false));
	}

	@Test
	void ettNyttUppdragForSammaNastaArErsatterDetForraUtanAttDetGallerEnDag() throws Exception {
		LocalDate nastaAr = idag.plusYears(1).withDayOfYear(1);
		utse(admin, ANNA, nastaAr).andExpect(status().isCreated());

		utse(admin, BERRA, nastaAr).andExpect(status().isCreated());

		assertThat(antalUppdrag()).isEqualTo(2);
		assertThat(avslutade(ANNA)).isTrue();
		assertThat(avslutade(BERRA)).isFalse();
		// Fortfarande bara ett utsett uppdrag för nästa år, och det är Berras.
		current(admin).andExpect(jsonPath("$.nextYearAssigned").value(true));
	}

	@Test
	void ettUppdragSomBorjarMittINastaArAvslutarDetForraDagenInnan() throws Exception {
		LocalDate nastaAr = idag.plusYears(1).withDayOfYear(1);
		LocalDate mitt = nastaAr.plusMonths(6);
		utse(admin, ANNA, nastaAr).andExpect(status().isCreated());

		utse(admin, BERRA, mitt).andExpect(status().isCreated());

		// Annas uppdrag gäller till och med dagen före Berras. Inget av dem gäller i dag.
		assertThat(avslutade(ANNA)).isTrue();
		assertThat(avslutade(BERRA)).isFalse();
		assertThat(holder()).isNull();
		assertThat(tokenversion()).isZero();
		current(admin).andExpect(jsonPath("$.assigned").value(false))
			.andExpect(jsonPath("$.nextYearAssigned").value(true));
	}

	@Test
	void ettUppdragSomBorjarSenareLamnarEttUppdragIDagUnangetTillStartdatumet() throws Exception {
		utse(admin, ANNA, idag).andExpect(status().isCreated());

		utse(admin, BERRA, idag.plusYears(1).withDayOfYear(1).plusMonths(6)).andExpect(status().isCreated());

		// Annas uppdrag i dag berörs inte, och Berras börjar först nästa år.
		assertThat(holder()).isEqualTo(ANNA);
		current(admin).andExpect(jsonPath("$.memberId").value(ANNA.toString()))
			.andExpect(jsonPath("$.endDate").value(ultimo(idag).toString()));
	}

	// ---- Validering

	@Test
	void inaktivMedlemGer400OchAndrarInget() throws Exception {
		utse(admin, INAKTIV, idag).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(DocumenterAppointmentService.INACTIVE_MEMBER));

		assertOforandrat();
	}

	@Test
	void okandMedlemGer400() throws Exception {
		utse(admin, UUID.randomUUID(), idag).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(DocumenterAppointmentService.UNKNOWN_MEMBER));

		assertOforandrat();
	}

	@Test
	void startdatumGransvardenIDagOchIGar() throws Exception {
		utse(admin, ANNA, idag.minusDays(1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(DocumenterAppointmentService.START_IN_THE_PAST));
		assertOforandrat();

		utse(admin, ANNA, idag).andExpect(status().isCreated());
	}

	@Test
	void startdatumGransvardenSistaDagenNastaArOchForstaDagenArEfter() throws Exception {
		utse(admin, ANNA, ultimo(idag.plusYears(1)).plusDays(1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(DocumenterAppointmentService.START_TOO_FAR_AHEAD));
		assertOforandrat();

		utse(admin, ANNA, ultimo(idag.plusYears(1))).andExpect(status().isCreated())
			.andExpect(jsonPath("$.endDate").value(ultimo(idag.plusYears(1)).toString()));
	}

	@Test
	void saknadeFaltGer400MedFaltlista() throws Exception {
		skicka(admin, "{}").andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.length()").value(2));
		skicka(admin, """
				{"startDate": "%s"}
				""".formatted(idag)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("memberId"));
		skicka(admin, """
				{"memberId": "%s"}
				""".formatted(ANNA)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("startDate"));

		assertOforandrat();
	}

	@Test
	void ogiltigaVardenGer400() throws Exception {
		skicka(admin, """
				{"memberId": "inte-ett-id", "startDate": "%s"}
				""".formatted(idag)).andExpect(status().isBadRequest());
		skicka(admin, """
				{"memberId": "%s", "startDate": "i morgon"}
				""".formatted(ANNA)).andExpect(status().isBadRequest());
		skicka(admin, """
				{"memberId": "%s", "startDate": "2026-02-30"}
				""".formatted(ANNA)).andExpect(status().isBadRequest());
		skicka(admin, "inte json").andExpect(status().isBadRequest());

		assertOforandrat();
	}

	// ---- Behörighet

	@Test
	void anonymtAnropGer401() throws Exception {
		mockMvc
			.perform(post("/api/v1/documenter-assignment").with(csrfToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(ANNA, idag)))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401));

		assertOforandrat();
	}

	@Test
	void dokumenterareFar403OchIngetAndras() throws Exception {
		utse(dokumenterare, ANNA, idag).andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403));

		assertOforandrat();
	}

	@Test
	void utanCsrfTokenGer403() throws Exception {
		mockMvc
			.perform(post("/api/v1/documenter-assignment").cookie(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(ANNA, idag)))
			.andExpect(status().isForbidden());

		assertOforandrat();
	}

	// ---- Ändringsloggen

	@Test
	void uppdragetOchInnehavarbytetLoggas() throws Exception {
		String id = utse(admin, ANNA, idag).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString()
			.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

		mockMvc.perform(get("/api/v1/audit-log").param("entity", "DOCUMENTER_ASSIGNMENT").param("id", id).cookie(admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].action").value("CREATED"))
			.andExpect(jsonPath("$[0].accountRole").value("ADMIN"))
			.andExpect(jsonPath("$[0].changes[?(@.field == 'memberId')].after").value(ANNA.toString()))
			.andExpect(jsonPath("$[0].changes[?(@.field == 'type')].after").value("ORDINARY"))
			.andExpect(jsonPath("$[0].changes[?(@.field == 'startDate')].after").value(idag.toString()))
			.andExpect(jsonPath("$[0].changes[?(@.field == 'endDate')].after").value(ultimo(idag).toString()));
		List<Map<String, Object>> konto = jdbc.sql("""
				SELECT a.action, c.field, c.after_value FROM audit_log a
				JOIN audit_log_change c ON c.audit_log_id = a.id WHERE a.entity_type = 'ACCOUNT'
				""").query().listOfRows();
		assertThat(konto).singleElement()
			.satisfies(rad -> assertThat(rad).containsEntry("field", "holderMemberId")
				.containsEntry("after_value", ANNA.toString()));
	}

	@Test
	void ettAvslutatUppdragLoggasSomUppdaterat() throws Exception {
		utse(admin, ANNA, idag).andExpect(status().isCreated());
		UUID annasUppdrag = jdbc.sql("SELECT id FROM documenter_assignment WHERE member_id = ?")
			.param(ANNA)
			.query(UUID.class)
			.single();

		utse(admin, BERRA, idag).andExpect(status().isCreated());

		mockMvc
			.perform(get("/api/v1/audit-log").param("entity", "DOCUMENTER_ASSIGNMENT")
				.param("id", annasUppdrag.toString())
				.cookie(admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].action").value("UPDATED"))
			.andExpect(jsonPath("$[0].changes[0].field").value("endedAt"))
			.andExpect(jsonPath("$[1].action").value("CREATED"));
	}

	@Test
	void ettMisslyckatAnropLoggasInte() throws Exception {
		utse(admin, INAKTIV, idag).andExpect(status().isBadRequest());
		utse(dokumenterare, ANNA, idag).andExpect(status().isForbidden());

		assertThat(jdbc.sql("SELECT count(*) FROM audit_log").query(Long.class).single()).isZero();
	}

	@Test
	void sammaMedlemIgenLoggasInteIgen() throws Exception {
		utse(admin, ANNA, idag).andExpect(status().isCreated());
		long fore = jdbc.sql("SELECT count(*) FROM audit_log").query(Long.class).single();

		utse(admin, ANNA, idag).andExpect(status().isOk());

		assertThat(jdbc.sql("SELECT count(*) FROM audit_log").query(Long.class).single()).isEqualTo(fore);
	}

	// ---- Hjälpmetoder

	private ResultActions utse(Cookie cookie, UUID medlem, LocalDate start) throws Exception {
		return skicka(cookie, json(medlem, start));
	}

	private ResultActions skicka(Cookie cookie, String body) throws Exception {
		return mockMvc.perform(post("/api/v1/documenter-assignment").with(csrfToken())
			.cookie(cookie)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private static String json(UUID medlem, LocalDate start) {
		return """
				{"memberId": "%s", "startDate": "%s"}
				""".formatted(medlem, start);
	}

	private ResultActions current(Cookie cookie) throws Exception {
		return mockMvc.perform(get("/api/v1/documenter-assignment/current").cookie(cookie)).andExpect(status().isOk());
	}

	/** Efter ett avvisat anrop är varken uppdrag, innehavare, tokenversion eller logg ändrade. */
	private void assertOforandrat() {
		assertThat(antalUppdrag()).isZero();
		assertThat(holder()).isNull();
		assertThat(tokenversion()).isZero();
		assertThat(jdbc.sql("SELECT count(*) FROM audit_log").query(Long.class).single()).isZero();
	}

	private long antalUppdrag() {
		return jdbc.sql("SELECT count(*) FROM documenter_assignment").query(Long.class).single();
	}

	private boolean avslutade(UUID medlem) {
		return jdbc.sql("SELECT ended_at IS NOT NULL FROM documenter_assignment WHERE member_id = ?")
			.param(medlem)
			.query(Boolean.class)
			.single();
	}

	private UUID holder() {
		return jdbc.sql("SELECT holder_member_id FROM user_account WHERE username = 'dokumenterare'")
			.query(UUID.class)
			.list()
			.getFirst();
	}

	private int tokenversion() {
		return jdbc.sql("SELECT token_version FROM user_account WHERE username = 'dokumenterare'")
			.query(Integer.class)
			.single();
	}

	private String hash() {
		return jdbc.sql("SELECT password_hash FROM user_account WHERE username = 'dokumenterare'")
			.query(String.class)
			.single();
	}

	private static LocalDate ultimo(LocalDate date) {
		return LocalDate.of(date.getYear(), 12, 31);
	}

	private void medlem(UUID id, String fornamn, String efternamn, String status) {
		jdbc.sql("""
				INSERT INTO member (id, first_name, last_name, birth_date, home_municipality, status)
				VALUES (:id, :fornamn, :efternamn, '1980-01-01', 'Trollhättan', :status)
				""")
			.param("id", id)
			.param("fornamn", fornamn)
			.param("efternamn", efternamn)
			.param("status", status)
			.update();
	}

	private void insattUppdrag(UUID medlem, String typ, LocalDate start, LocalDate slut) {
		jdbc.sql("""
				INSERT INTO documenter_assignment (member_id, type, start_date, end_date)
				VALUES (:medlem, :typ, :start, :slut)
				""").param("medlem", medlem).param("typ", typ).param("start", start).param("slut", slut).update();
	}

	private Cookie loggaIn(String username) throws Exception {
		return mockMvc
			.perform(post("/api/v1/auth/login").with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content("""
					{"username": "%s", "password": "%s"}
					""".formatted(username, LOSENORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie(AccessTokens.COOKIE_NAME);
	}

}
