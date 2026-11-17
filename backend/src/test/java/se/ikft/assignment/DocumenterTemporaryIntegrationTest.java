package se.ikft.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
 * {@code POST} och {@code DELETE /api/v1/documenter-assignment/temporary} mot en riktig Postgres. Datumen räknas
 * från dagens datum enligt den injicerade klockan. Jobbet som låter rollen gå tillbaka när perioden är slut
 * testas i {@link HolderSyncIntegrationTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DocumenterTemporaryIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	private static final String URL = "/api/v1/documenter-assignment/temporary";

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

	// ---- POST: lyckat fall

	@Test
	void ettTillfalligtUppdragGer201OchByterInnehavareDirekt() throws Exception {
		LocalDate slut = idag.plusDays(5);

		utse(admin, BERRA, slut).andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.id").isString())
			.andExpect(jsonPath("$.memberId").value(BERRA.toString()))
			.andExpect(jsonPath("$.memberName").value("Bertil Berg"))
			.andExpect(jsonPath("$.type").value("TEMPORARY"))
			.andExpect(jsonPath("$.startDate").value(idag.toString()))
			.andExpect(jsonPath("$.endDate").value(slut.toString()));

		assertThat(holder()).isEqualTo(BERRA);
		assertThat(tokenversion()).isEqualTo(1);
		assertThat(mustChangePassword()).isTrue();
		assertThat(antalUppdrag()).isEqualTo(1);
		current(admin).andExpect(jsonPath("$.memberId").value(BERRA.toString()))
			.andExpect(jsonPath("$.type").value("TEMPORARY"))
			.andExpect(jsonPath("$.endDate").value(slut.toString()));
	}

	@Test
	void innehavarensGamlaSessionArOgiltig() throws Exception {
		me(dokumenterare).andExpect(status().isOk());

		utse(admin, BERRA, idag.plusDays(1)).andExpect(status().isCreated());

		me(dokumenterare).andExpect(status().isUnauthorized());
		me(admin).andExpect(status().isOk());
	}

	@Test
	void ettTillfalligtUppdragGarForeDetOrdinarieSomBehallsOforandrat() throws Exception {
		insattUppdrag(ANNA, "ORDINARY", idag.withDayOfYear(1), LocalDate.of(idag.getYear(), 12, 31));
		jdbc.sql("UPDATE user_account SET holder_member_id = ? WHERE username = 'dokumenterare'").param(ANNA).update();

		utse(admin, BERRA, idag.plusDays(3)).andExpect(status().isCreated());

		assertThat(holder()).isEqualTo(BERRA);
		assertThat(antalUppdrag()).isEqualTo(2);
		assertThat(jdbc.sql("SELECT count(*) FROM documenter_assignment WHERE ended_at IS NOT NULL")
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	void sammaMedlemSomRedanArInnehavareGerIngetByte() throws Exception {
		insattUppdrag(ANNA, "ORDINARY", idag.withDayOfYear(1), LocalDate.of(idag.getYear(), 12, 31));
		jdbc.sql("UPDATE user_account SET holder_member_id = ? WHERE username = 'dokumenterare'").param(ANNA).update();

		utse(admin, ANNA, idag.plusDays(3)).andExpect(status().isCreated());

		assertThat(holder()).isEqualTo(ANNA);
		assertThat(tokenversion()).isZero();
		me(dokumenterare).andExpect(status().isOk());
	}

	@Test
	void uppdragetOchInnehavarbytetLoggas() throws Exception {
		utse(admin, BERRA, idag.plusDays(2)).andExpect(status().isCreated());

		List<Map<String, Object>> rader = jdbc.sql("""
				SELECT a.entity_type, a.action, c.field, c.after_value FROM audit_log a
				JOIN audit_log_change c ON c.audit_log_id = a.id ORDER BY a.seq, c.id
				""").query().listOfRows();
		assertThat(rader).extracting(rad -> rad.get("entity_type") + ":" + rad.get("field") + ":" + rad.get("action"))
			.contains("DOCUMENTER_ASSIGNMENT:type:CREATED", "DOCUMENTER_ASSIGNMENT:endDate:CREATED",
					"ACCOUNT:holderMemberId:UPDATED");
		assertThat(rader).filteredOn(rad -> "type".equals(rad.get("field")))
			.singleElement()
			.satisfies(rad -> assertThat(rad.get("after_value")).isEqualTo("TEMPORARY"));
	}

	// ---- POST: validering

	@Test
	void slutdatumGransvardenIDagOchIMorgon() throws Exception {
		utse(admin, BERRA, idag.minusDays(1)).andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(DocumenterAppointmentService.END_NOT_AFTER_TODAY));
		assertOforandrat();

		utse(admin, BERRA, idag).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(DocumenterAppointmentService.END_NOT_AFTER_TODAY));
		assertOforandrat();

		utse(admin, BERRA, idag.plusDays(1)).andExpect(status().isCreated());
	}

	@Test
	void inaktivMedlemGer400OchAndrarInget() throws Exception {
		utse(admin, INAKTIV, idag.plusDays(2)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(DocumenterAppointmentService.INACTIVE_MEMBER));

		assertOforandrat();
	}

	@Test
	void okandMedlemGer400() throws Exception {
		utse(admin, UUID.randomUUID(), idag.plusDays(2)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(DocumenterAppointmentService.UNKNOWN_MEMBER));

		assertOforandrat();
	}

	@Test
	void saknadeFaltGer400MedFaltlista() throws Exception {
		skicka(admin, "{}").andExpect(status().isBadRequest())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.errors.length()").value(2));
		skicka(admin, """
				{"endDate": "%s"}
				""".formatted(idag.plusDays(2))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("memberId"));
		skicka(admin, """
				{"memberId": "%s"}
				""".formatted(BERRA)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("endDate"));

		assertOforandrat();
	}

	@Test
	void ogiltigaVardenGer400() throws Exception {
		skicka(admin, """
				{"memberId": "inte-ett-id", "endDate": "%s"}
				""".formatted(idag.plusDays(2))).andExpect(status().isBadRequest());
		skicka(admin, """
				{"memberId": "%s", "endDate": "snart"}
				""".formatted(BERRA)).andExpect(status().isBadRequest());
		skicka(admin, "inte json").andExpect(status().isBadRequest());

		assertOforandrat();
	}

	@Test
	void ettPagaendeTillfalligtUppdragGer409OchAndrarInget() throws Exception {
		utse(admin, BERRA, idag.plusDays(5)).andExpect(status().isCreated());
		long loggFore = antalLoggposter();

		utse(admin, ANNA, idag.plusDays(2)).andExpect(status().isConflict())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(DocumenterAppointmentService.TEMPORARY_EXISTS));

		assertThat(holder()).isEqualTo(BERRA);
		assertThat(tokenversion()).isEqualTo(1);
		assertThat(antalUppdrag()).isEqualTo(1);
		assertThat(antalLoggposter()).isEqualTo(loggFore);
	}

	@Test
	void ettNyttTillfalligtUppdragGoarDetEfterAttDetForraAvslutats() throws Exception {
		utse(admin, BERRA, idag.plusDays(5)).andExpect(status().isCreated());
		avsluta(admin).andExpect(status().isNoContent());

		utse(admin, ANNA, idag.plusDays(2)).andExpect(status().isCreated());

		assertThat(holder()).isEqualTo(ANNA);
		current(admin).andExpect(jsonPath("$.memberId").value(ANNA.toString()));
	}

	// ---- POST: behörighet

	@Test
	void anonymtAnropGer401() throws Exception {
		mockMvc
			.perform(post(URL).with(csrfToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(BERRA, idag.plusDays(2))))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401));

		assertOforandrat();
	}

	@Test
	void dokumenterareFar403OchIngetAndras() throws Exception {
		utse(dokumenterare, BERRA, idag.plusDays(2)).andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403));

		assertOforandrat();
	}

	@Test
	void utanCsrfTokenGer403() throws Exception {
		mockMvc
			.perform(post(URL).cookie(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(BERRA, idag.plusDays(2))))
			.andExpect(status().isForbidden());

		assertOforandrat();
	}

	@Test
	void tvaSamtidigaUtseendenGerEttUppdragOchEtt409() throws Exception {
		for (int omgang = 0; omgang < 5; omgang++) {
			CountDownLatch klar = new CountDownLatch(1);
			try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
				List<Future<Integer>> svar = List.of(pool.submit(() -> {
					klar.await();
					return utse(admin, ANNA, idag.plusDays(3)).andReturn().getResponse().getStatus();
				}), pool.submit(() -> {
					klar.await();
					return utse(admin, BERRA, idag.plusDays(3)).andReturn().getResponse().getStatus();
				}));
				klar.countDown();
				assertThat(List.of(svar.get(0).get(), svar.get(1).get())).containsExactlyInAnyOrder(201, 409);
			}

			assertThat(antalUppdrag()).isEqualTo(1);
			UUID gallande = jdbc.sql("SELECT member_id FROM documenter_assignment").query(UUID.class).single();
			assertThat(holder()).isEqualTo(gallande);
			assertThat(tokenversion()).as("tokenversion i omgång %d", omgang).isEqualTo(1);
			aterstallInfor();
		}
	}

	// ---- DELETE

	@Test
	void ettAvslutIForvagGerRollenTillbakaTillDenOrdinarieOchRaknasSomInnehavarbyte() throws Exception {
		insattUppdrag(ANNA, "ORDINARY", idag.withDayOfYear(1), LocalDate.of(idag.getYear(), 12, 31));
		jdbc.sql("UPDATE user_account SET holder_member_id = ? WHERE username = 'dokumenterare'").param(ANNA).update();
		utse(admin, BERRA, idag.plusDays(5)).andExpect(status().isCreated());
		Cookie tillfallig = loggaIn("dokumenterare");
		me(tillfallig).andExpect(status().isOk());
		assertThat(tokenversion()).isEqualTo(1);

		avsluta(admin).andExpect(status().isNoContent());

		assertThat(holder()).isEqualTo(ANNA);
		assertThat(tokenversion()).isEqualTo(2);
		assertThat(mustChangePassword()).isTrue();
		me(tillfallig).andExpect(status().isUnauthorized());
		current(admin).andExpect(jsonPath("$.memberId").value(ANNA.toString()))
			.andExpect(jsonPath("$.type").value("ORDINARY"));
		assertThat(jdbc.sql("SELECT ended_at IS NOT NULL FROM documenter_assignment WHERE member_id = ?")
			.param(BERRA)
			.query(Boolean.class)
			.single()).isTrue();
		assertThat(jdbc.sql("SELECT ended_at IS NULL FROM documenter_assignment WHERE member_id = ?")
			.param(ANNA)
			.query(Boolean.class)
			.single()).isTrue();
	}

	@Test
	void ettAvslutUtanOrdinarieUppdragGerIngenInnehavare() throws Exception {
		utse(admin, BERRA, idag.plusDays(5)).andExpect(status().isCreated());

		avsluta(admin).andExpect(status().isNoContent());

		assertThat(holder()).isNull();
		assertThat(tokenversion()).isEqualTo(2);
		current(admin).andExpect(jsonPath("$.assigned").value(false));
	}

	@Test
	void ettTillfalligtUppdragSomAvslutasSammaDagAldrigHarGallt() throws Exception {
		utse(admin, BERRA, idag.plusDays(5)).andExpect(status().isCreated());

		avsluta(admin).andExpect(status().isNoContent());

		current(admin).andExpect(jsonPath("$.assigned").value(false));
		avsluta(admin).andExpect(status().isNotFound());
	}

	@Test
	void ettAvslutLoggasPaUppdragetOchKontot() throws Exception {
		utse(admin, BERRA, idag.plusDays(5)).andExpect(status().isCreated());
		UUID uppdrag = jdbc.sql("SELECT id FROM documenter_assignment").query(UUID.class).single();

		avsluta(admin).andExpect(status().isNoContent());

		mockMvc
			.perform(get("/api/v1/audit-log").param("entity", "DOCUMENTER_ASSIGNMENT")
				.param("id", uppdrag.toString())
				.cookie(admin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].action").value("UPDATED"))
			.andExpect(jsonPath("$[0].changes[0].field").value("endedAt"))
			.andExpect(jsonPath("$[1].action").value("CREATED"));
		List<Map<String, Object>> konto = jdbc.sql("""
				SELECT c.before_value, c.after_value FROM audit_log a
				JOIN audit_log_change c ON c.audit_log_id = a.id
				WHERE a.entity_type = 'ACCOUNT' ORDER BY a.seq DESC
				""").query().listOfRows();
		assertThat(konto.getFirst()).containsEntry("before_value", BERRA.toString()).containsEntry("after_value", null);
	}

	@Test
	void ettAvslutUtanTillfalligtUppdragGer404OchAndrarInget() throws Exception {
		insattUppdrag(ANNA, "ORDINARY", idag.withDayOfYear(1), LocalDate.of(idag.getYear(), 12, 31));
		jdbc.sql("UPDATE user_account SET holder_member_id = ? WHERE username = 'dokumenterare'").param(ANNA).update();

		avsluta(admin).andExpect(status().isNotFound())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value(DocumenterAppointmentService.NO_TEMPORARY));

		assertThat(holder()).isEqualTo(ANNA);
		assertThat(tokenversion()).isZero();
		assertThat(antalLoggposter()).isZero();
		assertThat(jdbc.sql("SELECT count(*) FROM documenter_assignment WHERE ended_at IS NOT NULL")
			.query(Long.class)
			.single()).isZero();
	}

	@Test
	void ettTillfalligtUppdragSomRedanLoptUtGer404() throws Exception {
		insattUppdrag(BERRA, "TEMPORARY", idag.minusDays(10), idag.minusDays(1));

		avsluta(admin).andExpect(status().isNotFound());
	}

	@Test
	void ettAvslutAnonymtGer401() throws Exception {
		utse(admin, BERRA, idag.plusDays(5)).andExpect(status().isCreated());

		mockMvc.perform(delete(URL).with(csrfToken()))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

		assertThat(holder()).isEqualTo(BERRA);
	}

	@Test
	void ettAvslutAvDokumenterareGer403OchAndrarInget() throws Exception {
		utse(admin, BERRA, idag.plusDays(5)).andExpect(status().isCreated());
		Cookie innehavare = loggaIn("dokumenterare");

		avsluta(innehavare).andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403));

		assertThat(holder()).isEqualTo(BERRA);
		assertThat(tokenversion()).isEqualTo(1);
	}

	@Test
	void ettAvslutUtanCsrfTokenGer403() throws Exception {
		utse(admin, BERRA, idag.plusDays(5)).andExpect(status().isCreated());

		mockMvc.perform(delete(URL).cookie(admin)).andExpect(status().isForbidden());

		assertThat(holder()).isEqualTo(BERRA);
		assertThat(tokenversion()).isEqualTo(1);
	}

	@Test
	void tvaSamtidigaAvslutGerEtt204OchEtt404OchEnTokenversionTillagd() throws Exception {
		for (int omgang = 0; omgang < 5; omgang++) {
			insattUppdrag(ANNA, "ORDINARY", idag.withDayOfYear(1), LocalDate.of(idag.getYear(), 12, 31));
			utse(admin, BERRA, idag.plusDays(5)).andExpect(status().isCreated());
			assertThat(tokenversion()).isEqualTo(1);

			CountDownLatch klar = new CountDownLatch(1);
			try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
				List<Future<Integer>> svar = List.of(pool.submit(() -> {
					klar.await();
					return avsluta(admin).andReturn().getResponse().getStatus();
				}), pool.submit(() -> {
					klar.await();
					return avsluta(admin).andReturn().getResponse().getStatus();
				}));
				klar.countDown();
				assertThat(List.of(svar.get(0).get(), svar.get(1).get())).containsExactlyInAnyOrder(204, 404);
			}

			assertThat(holder()).isEqualTo(ANNA);
			assertThat(tokenversion()).as("tokenversion i omgång %d", omgang).isEqualTo(2);
			aterstallInfor();
		}
	}

	// ---- Hjälpmetoder

	private ResultActions utse(Cookie cookie, UUID medlem, LocalDate slut) throws Exception {
		return skicka(cookie, json(medlem, slut));
	}

	private ResultActions skicka(Cookie cookie, String body) throws Exception {
		return mockMvc.perform(post(URL).with(csrfToken()).cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions avsluta(Cookie cookie) throws Exception {
		return mockMvc.perform(delete(URL).with(csrfToken()).cookie(cookie));
	}

	private static String json(UUID medlem, LocalDate slut) {
		return """
				{"memberId": "%s", "endDate": "%s"}
				""".formatted(medlem, slut);
	}

	private ResultActions current(Cookie cookie) throws Exception {
		return mockMvc.perform(get("/api/v1/documenter-assignment/current").cookie(cookie)).andExpect(status().isOk());
	}

	private ResultActions me(Cookie cookie) throws Exception {
		return mockMvc.perform(get("/api/v1/auth/me").cookie(cookie));
	}

	/** Efter ett avvisat anrop är varken uppdrag, innehavare, tokenversion eller logg ändrade. */
	private void assertOforandrat() {
		assertThat(antalUppdrag()).isZero();
		assertThat(holder()).isNull();
		assertThat(tokenversion()).isZero();
		assertThat(antalLoggposter()).isZero();
	}

	/** Tillbaka till början inför nästa omgång i ett racetest, med medlemmarna och Admins session kvar. */
	private void aterstallInfor() {
		jdbc.sql("DELETE FROM audit_log_change").update();
		jdbc.sql("DELETE FROM audit_log").update();
		jdbc.sql("DELETE FROM documenter_assignment").update();
		jdbc.sql("""
				UPDATE user_account SET token_version = 0, holder_member_id = NULL, must_change_password = false
				WHERE username = 'dokumenterare'
				""").update();
	}

	private long antalUppdrag() {
		return jdbc.sql("SELECT count(*) FROM documenter_assignment").query(Long.class).single();
	}

	private long antalLoggposter() {
		return jdbc.sql("SELECT count(*) FROM audit_log").query(Long.class).single();
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

	private boolean mustChangePassword() {
		return jdbc.sql("SELECT must_change_password FROM user_account WHERE username = 'dokumenterare'")
			.query(Boolean.class)
			.single();
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
