package se.ikft.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Innehavarkontrollen ({@link HolderSyncJob}) mot en riktig Postgres, med jobbet anropat direkt. Testkontexten
 * har den riktiga klockan, så uppdragen läggs in med datum som räknas från dagens datum: ett uppdrag från
 * förra året har gått ut, ett som börjar i dag har börjat gälla. Klockans gränser vid årsskiftet testas i
 * {@link DocumenterAppointmentServiceTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class HolderSyncIntegrationTest {

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
	Clock clock;

	@Autowired
	HolderSyncJob job;

	@Autowired
	DocumenterAppointmentService appointments;

	Cookie admin;

	LocalDate idag;

	@BeforeEach
	void forbered() throws Exception {
		stada();
		idag = LocalDate.now(clock);
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
		medlem(ANNA, "Anna", "Andersson");
		medlem(BERRA, "Bertil", "Berg");
		admin = loggaIn("admin");
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

	// ---- De tre fallen

	@Test
	void ettTillfalligtUppdragSomLoptUtGerRollenTillbakaOchDenTillfalligasSessionGer401() throws Exception {
		ordinarie(ANNA);
		insattUppdrag(BERRA, "TEMPORARY", idag.minusDays(10), idag.minusDays(1));
		setHolder(BERRA);
		Cookie tillfallig = loggaIn("dokumenterare");
		me(tillfallig).andExpect(status().isOk());

		job.sync("test");

		assertThat(holder()).isEqualTo(ANNA);
		assertThat(tokenversion()).isEqualTo(1);
		assertThat(mustChangePassword()).isTrue();
		me(tillfallig).andExpect(status().isUnauthorized());
		// Lösenordet behålls, så den ordinarie kan logga in med det och måste byta det.
		me(loggaIn("dokumenterare")).andExpect(status().isOk())
			.andExpect(jsonPath("$.holderName").value("Anna Andersson"))
			.andExpect(jsonPath("$.mustChangePassword").value(true));
		current(admin).andExpect(jsonPath("$.memberId").value(ANNA.toString()))
			.andExpect(jsonPath("$.type").value("ORDINARY"));
	}

	@Test
	void ettUppdragSomLagtsIForvagOchBorjarIDagGerInnehavaren() throws Exception {
		// Förra årets uppdrag har gått ut, och Berras uppdrag lades in tidigare och börjar i dag.
		insattUppdrag(ANNA, "ORDINARY", LocalDate.of(idag.getYear() - 1, 1, 1),
				LocalDate.of(idag.getYear() - 1, 12, 31));
		insattUppdrag(BERRA, "ORDINARY", idag, LocalDate.of(idag.getYear(), 12, 31));
		setHolder(ANNA);

		job.sync("test");

		assertThat(holder()).isEqualTo(BERRA);
		assertThat(tokenversion()).isEqualTo(1);
		current(admin).andExpect(jsonPath("$.memberId").value(BERRA.toString()));
	}

	@Test
	void ettUppdragSomBorjarSenareGerInteInnehavareForeStartdatumet() throws Exception {
		ordinarie(ANNA);
		setHolder(ANNA);
		insattUppdrag(BERRA, "TEMPORARY", idag.plusDays(1), idag.plusDays(5));

		job.sync("test");

		assertThat(holder()).isEqualTo(ANNA);
		assertThat(tokenversion()).isZero();
	}

	@Test
	void ettArsskifteUtanNagotUppdragGerKontotIngenInnehavareOchLoggasUtanInloggadAnvandare() throws Exception {
		insattUppdrag(ANNA, "ORDINARY", LocalDate.of(idag.getYear() - 1, 1, 1),
				LocalDate.of(idag.getYear() - 1, 12, 31));
		setHolder(ANNA);
		Cookie gammal = loggaIn("dokumenterare");

		job.sync("test");

		assertThat(holder()).isNull();
		assertThat(tokenversion()).isEqualTo(1);
		me(gammal).andExpect(status().isUnauthorized());
		current(admin).andExpect(jsonPath("$.assigned").value(false));
		List<Map<String, Object>> rader = jdbc.sql("""
				SELECT a.account_id, a.entity_type, a.action, c.field, c.before_value, c.after_value
				FROM audit_log a JOIN audit_log_change c ON c.audit_log_id = a.id
				""").query().listOfRows();
		assertThat(rader).singleElement().satisfies(rad -> {
			assertThat(rad).containsEntry("entity_type", "ACCOUNT")
				.containsEntry("action", "UPDATED")
				.containsEntry("field", "holderMemberId")
				.containsEntry("before_value", ANNA.toString())
				.containsEntry("after_value", null);
			// Jobbet körs av systemet, inte av en inloggad användare.
			assertThat(rad.get("account_id")).isNull();
		});
	}

	// ---- Upprepade körningar

	@Test
	void upprepadeKorningarGerSammaResultatOchRaknarBaraUppTokenversionenEnGang() {
		ordinarie(ANNA);
		insattUppdrag(BERRA, "TEMPORARY", idag.minusDays(10), idag.minusDays(1));
		setHolder(BERRA);

		for (int i = 0; i < 4; i++) {
			job.sync("test " + i);
		}

		assertThat(holder()).isEqualTo(ANNA);
		assertThat(tokenversion()).isEqualTo(1);
		assertThat(antalLoggposter()).isEqualTo(1);
	}

	@Test
	void arInnehavarenRedanRattHanderIngetOchIngenLoggpostSkrivs() {
		ordinarie(ANNA);
		setHolder(ANNA);

		job.sync("test");
		job.sync("test");

		assertThat(holder()).isEqualTo(ANNA);
		assertThat(tokenversion()).isZero();
		assertThat(mustChangePassword()).isFalse();
		assertThat(antalLoggposter()).isZero();
	}

	@Test
	void arKontotRedanUtanInnehavareOchIngetUppdragGallerHanderIngenting() {
		job.sync("test");

		assertThat(holder()).isNull();
		assertThat(tokenversion()).isZero();
		assertThat(antalLoggposter()).isZero();
	}

	@Test
	void uppstartskorningenGorSammaSakSomMidnattsjobbet() throws Exception {
		ordinarie(ANNA);
		insattUppdrag(BERRA, "TEMPORARY", idag.minusDays(10), idag.minusDays(1));
		setHolder(BERRA);

		job.run(null);

		assertThat(holder()).isEqualTo(ANNA);
		assertThat(tokenversion()).isEqualTo(1);
	}

	@Test
	void syncHolderRedovisarOmInnehavarenAndrades() {
		ordinarie(ANNA);
		setHolder(BERRA);

		assertThat(appointments.syncHolder()).isTrue();
		assertThat(appointments.syncHolder()).isFalse();
	}

	// ---- Samtidiga körningar

	/**
	 * Racet körs flera gånger, eftersom ett fel i låsningen bara syns när antalTradarna hamnar i en viss ordning. Alla
	 * körningar ser samma föråldrade innehavare, men bara en får byta den. Ett fel gav annars en
	 * tokenversion som räknats upp flera gånger.
	 */
	@Test
	void flerSamtidigaKorningarByterInnehavarenEnGangOchTokenversionenRaknasUppEnGang() throws Exception {
		for (int omgang = 0; omgang < 5; omgang++) {
			ordinarie(ANNA);
			insattUppdrag(BERRA, "TEMPORARY", idag.minusDays(10), idag.minusDays(1));
			setHolder(BERRA);
			int antalTradar = 4;
			CountDownLatch klar = new CountDownLatch(1);
			List<Boolean> andrade = new ArrayList<>();
			try (ExecutorService pool = Executors.newFixedThreadPool(antalTradar)) {
				List<Future<Boolean>> svar = new ArrayList<>();
				for (int i = 0; i < antalTradar; i++) {
					svar.add(pool.submit(() -> {
						klar.await();
						return appointments.syncHolder();
					}));
				}
				klar.countDown();
				for (Future<Boolean> resultat : svar) {
					andrade.add(resultat.get());
				}
			}

			assertThat(andrade).as("omgång %d", omgang).containsOnlyOnce(true);
			assertThat(holder()).isEqualTo(ANNA);
			assertThat(tokenversion()).as("tokenversion i omgång %d", omgang).isEqualTo(1);
			assertThat(antalLoggposter()).isEqualTo(1);
			aterstall();
		}
	}

	@Test
	void jobbetOchEttAvslutAvTillfalligtUppdragSamtidigtRaknarBaraUppTokenversionenEnGang() throws Exception {
		for (int omgang = 0; omgang < 5; omgang++) {
			ordinarie(ANNA);
			medTillfalligtIDag(BERRA);
			assertThat(tokenversion()).isEqualTo(1);

			CountDownLatch klar = new CountDownLatch(1);
			try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
				Future<?> jobbet = pool.submit(() -> {
					klar.await();
					return appointments.syncHolder();
				});
				Future<Integer> avslut = pool.submit(() -> {
					klar.await();
					return mockMvc
						.perform(delete("/api/v1/documenter-assignment/temporary").with(csrfToken()).cookie(admin))
						.andReturn()
						.getResponse()
						.getStatus();
				});
				klar.countDown();
				jobbet.get();
				assertThat(avslut.get()).isEqualTo(204);
			}

			// Oavsett ordning: Berra var innehavare (1), och rollen gick tillbaka till Anna en gång (2).
			assertThat(holder()).isEqualTo(ANNA);
			assertThat(tokenversion()).as("tokenversion i omgång %d", omgang).isEqualTo(2);
			aterstall();
		}
	}

	@Test
	void jobbetOchEttNyttTillfalligtUppdragSamtidigtGerBerraEnGangOchEnTokenversion() throws Exception {
		for (int omgang = 0; omgang < 5; omgang++) {
			ordinarie(ANNA);
			setHolder(ANNA);

			CountDownLatch klar = new CountDownLatch(1);
			try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
				Future<?> jobbet = pool.submit(() -> {
					klar.await();
					return appointments.syncHolder();
				});
				Future<Integer> utse = pool.submit(() -> {
					klar.await();
					return mockMvc
						.perform(post("/api/v1/documenter-assignment/temporary").with(csrfToken())
							.cookie(admin)
							.contentType(MediaType.APPLICATION_JSON)
							.content("""
									{"memberId": "%s", "endDate": "%s"}
									""".formatted(BERRA, idag.plusDays(3))))
						.andReturn()
						.getResponse()
						.getStatus();
				});
				klar.countDown();
				jobbet.get();
				assertThat(utse.get()).isEqualTo(201);
			}

			assertThat(holder()).isEqualTo(BERRA);
			assertThat(tokenversion()).as("tokenversion i omgång %d", omgang).isEqualTo(1);
			aterstall();
		}
	}

	// ---- Hjälpmetoder

	/** Ett ordinarie uppdrag som gäller i dag, till och med årets slut. */
	private void ordinarie(UUID medlem) {
		insattUppdrag(medlem, "ORDINARY", idag.withDayOfYear(1), LocalDate.of(idag.getYear(), 12, 31));
	}

	/** Ett tillfälligt uppdrag som börjar i dag, med innehavaren satt på det sätt som Admin-anropet gör. */
	private void medTillfalligtIDag(UUID medlem) {
		insattUppdrag(medlem, "TEMPORARY", idag, idag.plusDays(5));
		setHolder(medlem);
		jdbc.sql("UPDATE user_account SET token_version = 1 WHERE username = 'dokumenterare'").update();
	}

	private ResultActions current(Cookie cookie) throws Exception {
		return mockMvc.perform(get("/api/v1/documenter-assignment/current").cookie(cookie)).andExpect(status().isOk());
	}

	private ResultActions me(Cookie cookie) throws Exception {
		return mockMvc.perform(get("/api/v1/auth/me").cookie(cookie));
	}

	/** Tillbaka till början inför nästa omgång i ett racetest, med medlemmarna och Admins session kvar. */
	private void aterstall() {
		jdbc.sql("DELETE FROM audit_log_change").update();
		jdbc.sql("DELETE FROM audit_log").update();
		jdbc.sql("DELETE FROM documenter_assignment").update();
		jdbc.sql("""
				UPDATE user_account SET token_version = 0, holder_member_id = NULL, must_change_password = false
				WHERE username = 'dokumenterare'
				""").update();
	}

	private void setHolder(UUID medlem) {
		jdbc.sql("UPDATE user_account SET holder_member_id = ? WHERE username = 'dokumenterare'").param(medlem).update();
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

	private void medlem(UUID id, String fornamn, String efternamn) {
		jdbc.sql("""
				INSERT INTO member (id, first_name, last_name, birth_date, home_municipality, status)
				VALUES (:id, :fornamn, :efternamn, '1980-01-01', 'Trollhättan', 'ACTIVE')
				""").param("id", id).param("fornamn", fornamn).param("efternamn", efternamn).update();
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
