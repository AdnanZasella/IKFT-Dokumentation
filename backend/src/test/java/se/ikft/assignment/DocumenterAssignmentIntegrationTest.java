package se.ikft.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import se.ikft.TestcontainersConfiguration;
import se.ikft.security.AccessTokens;

/**
 * {@code GET /api/v1/documenter-assignment/current} och tabellen {@code documenter_assignment} mot en riktig
 * Postgres. Datumen räknas från dagens datum, eftersom testkontexten har den riktiga klockan. Gränsvärdena kring
 * årsskiftet testas med styrd klocka i {@link DocumenterAssignmentServiceTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DocumenterAssignmentIntegrationTest {

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
	DocumenterAssignmentRepository repository;

	@Autowired
	AssignmentProperties properties;

	Cookie admin;

	Cookie dokumenterare;

	LocalDate idag;

	@BeforeEach
	void forbered() throws Exception {
		stada();
		idag = LocalDate.now(clock);
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
		medlem(ANNA, "Anna", "Andersson");
		medlem(BERRA, "Bertil", "Berg");
		admin = loggaIn("admin");
		dokumenterare = loggaIn("dokumenterare");
	}

	@AfterEach
	void stada() {
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0, holder_member_id = NULL").update();
		jdbc.sql("DELETE FROM documenter_assignment").update();
		jdbc.sql("DELETE FROM member").update();
	}

	@Test
	void inagetUppdragGerAssignedFalse() throws Exception {
		current(admin).andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.assigned").value(false))
			.andExpect(jsonPath("$.memberId").isEmpty())
			.andExpect(jsonPath("$.memberName").isEmpty())
			.andExpect(jsonPath("$.type").isEmpty())
			.andExpect(jsonPath("$.endDate").isEmpty());
	}

	@Test
	void ordinarieUppdragVisasMedNamnTypOchSlutdatum() throws Exception {
		uppdrag(ANNA, "ORDINARY", idag.withDayOfYear(1), ultimo(idag));

		current(admin).andExpect(status().isOk())
			.andExpect(jsonPath("$.assigned").value(true))
			.andExpect(jsonPath("$.memberId").value(ANNA.toString()))
			.andExpect(jsonPath("$.memberName").value("Anna Andersson"))
			.andExpect(jsonPath("$.type").value("ORDINARY"))
			.andExpect(jsonPath("$.endDate").value(ultimo(idag).toString()));
	}

	@Test
	void dokumenterareSerSammaUppdragMenInteAdminFalten() throws Exception {
		uppdrag(ANNA, "ORDINARY", idag.withDayOfYear(1), ultimo(idag));

		current(dokumenterare).andExpect(status().isOk())
			.andExpect(jsonPath("$.assigned").value(true))
			.andExpect(jsonPath("$.memberName").value("Anna Andersson"))
			.andExpect(jsonPath("$.nextYearAssigned").doesNotExist())
			.andExpect(jsonPath("$.reminder").doesNotExist());
	}

	@Test
	void adminFarOckssaNastaArOchPaminnelse() throws Exception {
		boolean forvantadPaminnelse = !idag.isBefore(properties.reminderFrom().atYear(idag.getYear()));

		current(admin).andExpect(status().isOk())
			.andExpect(jsonPath("$.nextYearAssigned").value(false))
			.andExpect(jsonPath("$.reminder").value(forvantadPaminnelse));
	}

	@Test
	void nastaArUtsettGerNextYearAssignedOchIngenPaminnelse() throws Exception {
		LocalDate nastaAr = idag.plusYears(1).withDayOfYear(1);
		uppdrag(BERRA, "ORDINARY", nastaAr, ultimo(nastaAr));

		current(admin).andExpect(status().isOk())
			.andExpect(jsonPath("$.nextYearAssigned").value(true))
			.andExpect(jsonPath("$.reminder").value(false));
	}

	@Test
	void ettUppdragSomBorjarSenareNastaArRaknasOckssaSomUtsett() throws Exception {
		LocalDate sent = idag.plusYears(1).withDayOfYear(1).plusMonths(5);
		uppdrag(BERRA, "ORDINARY", sent, ultimo(sent));

		current(admin).andExpect(jsonPath("$.nextYearAssigned").value(true));
	}

	@Test
	void ettTillfalligtUppdragGarForeDetOrdinarie() throws Exception {
		uppdrag(ANNA, "ORDINARY", idag.withDayOfYear(1), ultimo(idag));
		uppdrag(BERRA, "TEMPORARY", idag.minusDays(1), idag.plusDays(5));

		current(admin).andExpect(jsonPath("$.memberId").value(BERRA.toString()))
			.andExpect(jsonPath("$.type").value("TEMPORARY"))
			.andExpect(jsonPath("$.endDate").value(idag.plusDays(5).toString()));
	}

	@Test
	void ettTillfalligtUppdragSomTagitSlutGerTillbakaDetOrdinarie() throws Exception {
		uppdrag(ANNA, "ORDINARY", idag.withDayOfYear(1), ultimo(idag));
		uppdrag(BERRA, "TEMPORARY", idag.minusDays(10), idag.minusDays(1));

		current(admin).andExpect(jsonPath("$.memberId").value(ANNA.toString()))
			.andExpect(jsonPath("$.type").value("ORDINARY"));
	}

	@Test
	void ettTillfalligtUppdragSomSlutarIdagGallerFortfarande() throws Exception {
		uppdrag(BERRA, "TEMPORARY", idag.minusDays(3), idag);

		current(admin).andExpect(jsonPath("$.memberId").value(BERRA.toString()));
	}

	@Test
	void ettUppdragSomAvslutatsIDagGallerInte() throws Exception {
		uppdrag(ANNA, "ORDINARY", idag.withDayOfYear(1), ultimo(idag));
		jdbc.sql("UPDATE documenter_assignment SET ended_at = ?")
			.param(java.sql.Timestamp.from(Instant.now(clock)))
			.update();

		current(admin).andExpect(jsonPath("$.assigned").value(false));
	}

	@Test
	void ettUppdragSomBorjarFramoverGallerInteAnnu() throws Exception {
		uppdrag(ANNA, "ORDINARY", idag.plusDays(1), ultimo(idag.plusDays(1)));

		// Ett uppdrag som börjar i morgon gäller inte i dag.
		current(admin).andExpect(jsonPath("$.assigned").value(false));
	}

	@Test
	void anonymtAnropGer401() throws Exception {
		mockMvc.perform(get("/api/v1/documenter-assignment/current"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(401));
	}

	@Test
	void ogiltigCookieGer401() throws Exception {
		mockMvc.perform(get("/api/v1/documenter-assignment/current")
			.cookie(new Cookie(AccessTokens.COOKIE_NAME, "inte.en.token"))).andExpect(status().isUnauthorized());
	}

	@Test
	void dokumenterareFarInteAndraUppdrag() throws Exception {
		// Endpointsen för att utse kommer i #17 och #18, men spärren för Admin finns redan på hela sökvägen.
		for (String path : new String[] { "/api/v1/documenter-assignment", "/api/v1/documenter-assignment/temporary" }) {
			mockMvc.perform(skicka(post(path), dokumenterare)).andExpect(status().isForbidden());
		}
	}

	// ---- Tabellen

	@Test
	void entitetenSparasOchLasesMedAllaFalt() {
		DocumenterAssignment ordinarie = repository
			.saveAndFlush(DocumenterAssignment.ordinary(ANNA, LocalDate.of(2026, 3, 1), Instant.now(clock)));
		DocumenterAssignment tillfalligt = repository.saveAndFlush(DocumenterAssignment.temporary(BERRA,
				LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10), Instant.now(clock)));

		DocumenterAssignment last = repository.findById(ordinarie.getId()).orElseThrow();
		assertThat(last.getMemberId()).isEqualTo(ANNA);
		assertThat(last.getType()).isEqualTo(AssignmentType.ORDINARY);
		assertThat(last.getStartDate()).isEqualTo(LocalDate.of(2026, 3, 1));
		assertThat(last.getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(last.getEndedAt()).isNull();
		assertThat(repository.findById(tillfalligt.getId()).orElseThrow().getType())
			.isEqualTo(AssignmentType.TEMPORARY);
		assertThat(jdbc.sql("SELECT type FROM documenter_assignment WHERE id = ?")
			.param(tillfalligt.getId())
			.query(String.class)
			.single()).isEqualTo("TEMPORARY");
	}

	@Test
	void avslutIForvagSparasMedTidpunkt() {
		DocumenterAssignment uppdrag = DocumenterAssignment.ordinary(ANNA, LocalDate.of(2026, 3, 1),
				Instant.now(clock));
		uppdrag.endEarly(Instant.parse("2026-05-01T10:00:00Z"));
		UUID id = repository.saveAndFlush(uppdrag).getId();

		assertThat(repository.findById(id).orElseThrow().getEndedAt()).isEqualTo(Instant.parse("2026-05-01T10:00:00Z"));
	}

	@Test
	void ettOrdinarieUppdragKanInteSlutaEfter31DecemberISammaAr() {
		assertThatThrownBy(() -> jdbc.sql("""
				INSERT INTO documenter_assignment (member_id, type, start_date, end_date)
				VALUES (:member, 'ORDINARY', '2026-03-01', '2027-01-01')
				""").param("member", ANNA).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void ettTillfalligtUppdragKanGaOverArsskiftet() {
		jdbc.sql("""
				INSERT INTO documenter_assignment (member_id, type, start_date, end_date)
				VALUES (:member, 'TEMPORARY', '2026-12-20', '2027-01-10')
				""").param("member", BERRA).update();

		assertThat(jdbc.sql("SELECT count(*) FROM documenter_assignment").query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void slutdatumForeStartdatumAvvisasAvDatabasen() {
		assertThatThrownBy(() -> jdbc.sql("""
				INSERT INTO documenter_assignment (member_id, type, start_date, end_date)
				VALUES (:member, 'TEMPORARY', '2026-06-10', '2026-06-09')
				""").param("member", BERRA).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void okandTypAvvisasAvDatabasen() {
		assertThatThrownBy(() -> jdbc.sql("""
				INSERT INTO documenter_assignment (member_id, type, start_date, end_date)
				VALUES (:member, 'PERMANENT', '2026-06-10', '2026-06-20')
				""").param("member", BERRA).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void okandMedlemAvvisasAvDatabasen() {
		assertThatThrownBy(() -> jdbc.sql("""
				INSERT INTO documenter_assignment (member_id, type, start_date, end_date)
				VALUES (:member, 'ORDINARY', '2026-06-10', '2026-06-20')
				""").param("member", UUID.randomUUID()).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	private ResultActions current(Cookie cookie) throws Exception {
		return mockMvc.perform(get("/api/v1/documenter-assignment/current").cookie(cookie));
	}

	private static MockHttpServletRequestBuilder skicka(MockHttpServletRequestBuilder request, Cookie cookie) {
		return request.cookie(cookie).with(csrfToken()).contentType(MediaType.APPLICATION_JSON).content("{}");
	}

	private static LocalDate ultimo(LocalDate date) {
		return LocalDate.of(date.getYear(), 12, 31);
	}

	private void medlem(UUID id, String fornamn, String efternamn) {
		jdbc.sql("""
				INSERT INTO member (id, first_name, last_name, birth_date, home_municipality)
				VALUES (:id, :fornamn, :efternamn, '1980-01-01', 'Trollhättan')
				""").param("id", id).param("fornamn", fornamn).param("efternamn", efternamn).update();
	}

	private void uppdrag(UUID medlem, String typ, LocalDate start, LocalDate slut) {
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
