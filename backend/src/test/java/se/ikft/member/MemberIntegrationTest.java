package se.ikft.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.time.Clock;
import java.time.LocalDate;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import se.ikft.TestcontainersConfiguration;
import se.ikft.security.AccessTokens;

/**
 * Medlemsregistret mot en riktig Postgres: behörighet, validering och vad varje roll får se.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MemberIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	private static final String OKAND_ID = "00000000-0000-0000-0000-00000000dead";

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

	@BeforeEach
	void forbered() throws Exception {
		stada();
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
		admin = loggaIn("admin");
		dokumenterare = loggaIn("dokumenterare");
	}

	@AfterEach
	void stada() {
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0, holder_member_id = NULL").update();
		jdbc.sql("DELETE FROM audit_log_change").update();
		jdbc.sql("DELETE FROM audit_log").update();
		jdbc.sql("DELETE FROM member_fifs_fee").update();
		jdbc.sql("DELETE FROM member_role").update();
		jdbc.sql("DELETE FROM member").update();
	}

	// ---- Skapa

	@Test
	void adminSkaparMedlem() throws Exception {
		perform(post("/api/v1/members"), admin, medlem("Prova", "Provsson", "2010-05-01"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.firstName").value("Prova"))
			.andExpect(jsonPath("$.lastName").value("Provsson"))
			.andExpect(jsonPath("$.birthDate").value("2010-05-01"))
			.andExpect(jsonPath("$.homeMunicipality").value("Trollhättan"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.ageGroup").value("YOUTH"))
			.andExpect(jsonPath("$.fifsFees").isEmpty())
			.andExpect(jsonPath("$.roles").isEmpty());

		assertThat(jdbc.sql("SELECT count(*) FROM member").query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void textenRensasFranMellanslagOchTomAdressBlirNull() throws Exception {
		perform(post("/api/v1/members"), admin, """
				{"firstName": "  Prova ", "lastName": " Provsson  ", "birthDate": "2010-05-01",
				 "address": "   ", "homeMunicipality": " Trollhättan "}
				""").andExpect(status().isCreated())
			.andExpect(jsonPath("$.firstName").value("Prova"))
			.andExpect(jsonPath("$.lastName").value("Provsson"))
			.andExpect(jsonPath("$.homeMunicipality").value("Trollhättan"))
			.andExpect(jsonPath("$.address").isEmpty());
	}

	@Test
	void enNyMedlemArAlltidAktivOavsettStatusIAnropet() throws Exception {
		perform(post("/api/v1/members"), admin, """
				{"firstName": "Prova", "lastName": "Provsson", "birthDate": "2010-05-01",
				 "homeMunicipality": "Trollhättan", "status": "INACTIVE"}
				""").andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void obligatoriskaFaltSaknasGer400MedFelPerFalt() throws Exception {
		perform(post("/api/v1/members"), admin, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field == 'firstName')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'lastName')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'birthDate')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'homeMunicipality')]").exists());

		assertThat(antalMedlemmar()).isZero();
	}

	@Test
	void blankaNamnAvvisas() throws Exception {
		perform(post("/api/v1/members"), admin, """
				{"firstName": "   ", "lastName": "", "birthDate": "2010-05-01", "homeMunicipality": "Trollhättan"}
				""").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field == 'firstName')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'lastName')]").exists());
	}

	@Test
	void forLangtNamnAvvisas() throws Exception {
		perform(post("/api/v1/members"), admin, medlem("A".repeat(101), "Provsson", "2010-05-01"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field == 'firstName')]").exists());
	}

	@Test
	void foddIFramtidenAvvisas() throws Exception {
		LocalDate imorgon = LocalDate.now(clock).plusDays(1);

		perform(post("/api/v1/members"), admin, medlem("Prova", "Provsson", imorgon.toString()))
			.andExpect(status().isBadRequest());

		assertThat(antalMedlemmar()).isZero();
	}

	@Test
	void foddIDagGarBra() throws Exception {
		perform(post("/api/v1/members"), admin, medlem("Prova", "Provsson", LocalDate.now(clock).toString()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.ageGroup").value("YOUNGER"));
	}

	@Test
	void orimligtGammaltFodelsedatumAvvisas() throws Exception {
		perform(post("/api/v1/members"), admin, medlem("Prova", "Provsson", "1899-12-31"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void medlemSedanForeFodelsedatumAvvisas() throws Exception {
		perform(post("/api/v1/members"), admin, """
				{"firstName": "Prova", "lastName": "Provsson", "birthDate": "2010-05-01",
				 "homeMunicipality": "Trollhättan", "memberSince": "2009-01-01"}
				""").andExpect(status().isBadRequest());
	}

	@Test
	void ogiltigtDatumGer400() throws Exception {
		perform(post("/api/v1/members"), admin, medlem("Prova", "Provsson", "inte-ett-datum"))
			.andExpect(status().isBadRequest());
	}

	@Test
	void personnummerLagrasInteOchOkandaFaltIgnoreras() throws Exception {
		perform(post("/api/v1/members"), admin, """
				{"firstName": "Prova", "lastName": "Provsson", "birthDate": "2010-05-01",
				 "homeMunicipality": "Trollhättan", "personnummer": "201005010000"}
				""").andExpect(status().isCreated()).andExpect(jsonPath("$.personnummer").doesNotExist());
	}

	// ---- Åldersgrupp

	@Test
	void aldersgruppenFoljerBidragetsGranser() throws Exception {
		LocalDate idag = LocalDate.now(clock);

		assertThat(aldersgruppFor(idag.minusYears(7).plusDays(1))).isEqualTo("YOUNGER"); // 6 år
		assertThat(aldersgruppFor(idag.minusYears(7))).isEqualTo("YOUTH"); // exakt 7 år
		assertThat(aldersgruppFor(idag.minusYears(26).plusDays(1))).isEqualTo("YOUTH"); // 25 år
		assertThat(aldersgruppFor(idag.minusYears(26))).isEqualTo("OLDER"); // exakt 26 år
	}

	// ---- Läsa

	@Test
	void adminHamtarMedlemMedAvgifterOchRoller() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "1985-06-15");
		perform(put("/api/v1/members/" + id + "/fifs-fees/2025"), admin, null).andExpect(status().isOk());
		perform(post("/api/v1/members/" + id + "/roles"), admin, """
				{"title": "Kassör", "startDate": "2025-01-01"}
				""").andExpect(status().isCreated());

		perform(get("/api/v1/members/" + id), admin, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(id))
			.andExpect(jsonPath("$.ageGroup").value("OLDER"))
			.andExpect(jsonPath("$.fifsFees[0].year").value(2025))
			.andExpect(jsonPath("$.roles[0].title").value("Kassör"));
	}

	@Test
	void okandMedlemGer404() throws Exception {
		perform(get("/api/v1/members/" + OKAND_ID), admin, null).andExpect(status().isNotFound());
	}

	@Test
	void ettIdSomInteArEttUuidGer400() throws Exception {
		perform(get("/api/v1/members/inte-ett-uuid"), admin, null).andExpect(status().isBadRequest());
	}

	// ---- Lista

	@Test
	void adminListanVisarAllaSorteradePaEfternamn() throws Exception {
		skapaMedlem("Zara", "Ahmed", "2010-01-01");
		skapaMedlem("Adam", "Svensson", "2010-01-01");
		String inaktiv = skapaMedlem("Bo", "Berg", "2010-01-01");
		perform(delete("/api/v1/members/" + inaktiv), admin, null).andExpect(status().isNoContent());

		perform(get("/api/v1/members"), admin, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].lastName").value("Ahmed"))
			.andExpect(jsonPath("$[1].lastName").value("Berg"))
			.andExpect(jsonPath("$[1].status").value("INACTIVE"))
			.andExpect(jsonPath("$[2].lastName").value("Svensson"))
			.andExpect(jsonPath("$[0].birthDate").value("2010-01-01"))
			.andExpect(jsonPath("$[0].homeMunicipality").value("Trollhättan"));
	}

	@Test
	void adminKanFiltreraListanPaStatus() throws Exception {
		skapaMedlem("Aktiv", "Aktivsson", "2010-01-01");
		String inaktiv = skapaMedlem("Inaktiv", "Inaktivsson", "2010-01-01");
		perform(delete("/api/v1/members/" + inaktiv), admin, null).andExpect(status().isNoContent());

		perform(get("/api/v1/members?status=INACTIVE"), admin, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].lastName").value("Inaktivsson"));
		perform(get("/api/v1/members?status=ACTIVE"), admin, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].lastName").value("Aktivsson"));
	}

	@Test
	void ogiltigtStatusfilterGer400() throws Exception {
		perform(get("/api/v1/members?status=NAGOT"), admin, null).andExpect(status().isBadRequest());
	}

	@Test
	void dokumenterarenSerBaraNamnOchAldersgruppForAktivaMedlemmar() throws Exception {
		skapaMedlem("Prova", "Provsson", "2010-01-01");
		String inaktiv = skapaMedlem("Gammal", "Medlem", "2010-01-01");
		perform(delete("/api/v1/members/" + inaktiv), admin, null).andExpect(status().isNoContent());

		perform(get("/api/v1/members"), dokumenterare, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").isNotEmpty())
			.andExpect(jsonPath("$[0].firstName").value("Prova"))
			.andExpect(jsonPath("$[0].lastName").value("Provsson"))
			.andExpect(jsonPath("$[0].ageGroup").value("YOUTH"))
			// Allt annat ligger kvar hos Admin.
			.andExpect(jsonPath("$[0].birthDate").doesNotExist())
			.andExpect(jsonPath("$[0].address").doesNotExist())
			.andExpect(jsonPath("$[0].homeMunicipality").doesNotExist())
			.andExpect(jsonPath("$[0].status").doesNotExist())
			.andExpect(jsonPath("$[0].guardianConsent").doesNotExist());
	}

	@Test
	void dokumenterarenKanInteFragaEfterInaktiva() throws Exception {
		String inaktiv = skapaMedlem("Gammal", "Medlem", "2010-01-01");
		perform(delete("/api/v1/members/" + inaktiv), admin, null).andExpect(status().isNoContent());

		perform(get("/api/v1/members?status=INACTIVE"), dokumenterare, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(0));
	}

	// ---- Uppdatera och inaktivera

	@Test
	void adminUppdarerMedlem() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(put("/api/v1/members/" + id), admin, """
				{"firstName": "Ändrad", "lastName": "Namn", "birthDate": "2008-03-02", "address": "Provgatan 1",
				 "homeMunicipality": "Vänersborg", "memberSince": "2020-01-01", "guardianConsent": true}
				""").andExpect(status().isOk())
			.andExpect(jsonPath("$.firstName").value("Ändrad"))
			.andExpect(jsonPath("$.birthDate").value("2008-03-02"))
			.andExpect(jsonPath("$.address").value("Provgatan 1"))
			.andExpect(jsonPath("$.homeMunicipality").value("Vänersborg"))
			.andExpect(jsonPath("$.memberSince").value("2020-01-01"))
			.andExpect(jsonPath("$.guardianConsent").value(true))
			// Utan status ändras inte statusen.
			.andExpect(jsonPath("$.status").value("ACTIVE"));

		perform(get("/api/v1/members/" + id), admin, null).andExpect(jsonPath("$.firstName").value("Ändrad"));
	}

	@Test
	void uppdateringMedOgiltigaUppgifterAndrarIngenting() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(put("/api/v1/members/" + id), admin, medlem("", "Provsson", "2010-05-01"))
			.andExpect(status().isBadRequest());

		perform(get("/api/v1/members/" + id), admin, null).andExpect(jsonPath("$.firstName").value("Prova"));
	}

	@Test
	void uppdateraOkandMedlemGer404() throws Exception {
		perform(put("/api/v1/members/" + OKAND_ID), admin, medlem("Prova", "Provsson", "2010-05-01"))
			.andExpect(status().isNotFound());
	}

	@Test
	void raderaInaktiverarMedlemmenOchRaderarInte() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(delete("/api/v1/members/" + id), admin, null).andExpect(status().isNoContent());

		assertThat(jdbc.sql("SELECT status FROM member WHERE id = ?::uuid").param(id).query(String.class).single())
			.isEqualTo("INACTIVE");
		perform(get("/api/v1/members/" + id), admin, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("INACTIVE"));
	}

	@Test
	void attInaktiveraTvaGangerGerSammaResultat() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(delete("/api/v1/members/" + id), admin, null).andExpect(status().isNoContent());
		perform(delete("/api/v1/members/" + id), admin, null).andExpect(status().isNoContent());
	}

	@Test
	void inaktivMedlemKanAterAktiverasMedStatus() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");
		perform(delete("/api/v1/members/" + id), admin, null).andExpect(status().isNoContent());

		perform(put("/api/v1/members/" + id), admin, """
				{"firstName": "Prova", "lastName": "Provsson", "birthDate": "2010-05-01",
				 "homeMunicipality": "Trollhättan", "status": "ACTIVE"}
				""").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
	}

	@Test
	void inaktiveraOkandMedlemGer404() throws Exception {
		perform(delete("/api/v1/members/" + OKAND_ID), admin, null).andExpect(status().isNotFound());
	}

	@Test
	void inaktiveringenBehallerAvgifterOchRoller() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "1985-06-15");
		perform(put("/api/v1/members/" + id + "/fifs-fees/2025"), admin, null).andExpect(status().isOk());
		perform(post("/api/v1/members/" + id + "/roles"), admin, """
				{"title": "Ordförande", "startDate": "2025-01-01"}
				""").andExpect(status().isCreated());

		perform(delete("/api/v1/members/" + id), admin, null).andExpect(status().isNoContent());

		perform(get("/api/v1/members/" + id), admin, null).andExpect(jsonPath("$.fifsFees.length()").value(1))
			.andExpect(jsonPath("$.roles.length()").value(1));
	}

	// ---- Föreningsroller

	@Test
	void adminRegistrerarRollMedOchUtanSlutdatum() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "1985-06-15");

		perform(post("/api/v1/members/" + id + "/roles"), admin, """
				{"title": " Kassör ", "startDate": "2025-01-01", "endDate": "2025-12-31"}
				""").andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isNotEmpty())
			.andExpect(jsonPath("$.title").value("Kassör"))
			.andExpect(jsonPath("$.startDate").value("2025-01-01"))
			.andExpect(jsonPath("$.endDate").value("2025-12-31"));
		perform(post("/api/v1/members/" + id + "/roles"), admin, """
				{"title": "Ordförande", "startDate": "2026-01-01"}
				""").andExpect(status().isCreated()).andExpect(jsonPath("$.endDate").isEmpty());

		// Senaste rollen först.
		perform(get("/api/v1/members/" + id), admin, null).andExpect(jsonPath("$.roles.length()").value(2))
			.andExpect(jsonPath("$.roles[0].title").value("Ordförande"))
			.andExpect(jsonPath("$.roles[1].title").value("Kassör"));
	}

	@Test
	void enRollMedSlutdatumForeStartdatumAvvisas() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "1985-06-15");

		perform(post("/api/v1/members/" + id + "/roles"), admin, """
				{"title": "Kassör", "startDate": "2025-06-01", "endDate": "2025-05-31"}
				""").andExpect(status().isBadRequest());

		perform(get("/api/v1/members/" + id), admin, null).andExpect(jsonPath("$.roles").isEmpty());
	}

	@Test
	void enRollMedSammaStartOchSlutdatumGarBra() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "1985-06-15");

		perform(post("/api/v1/members/" + id + "/roles"), admin, """
				{"title": "Mötesordförande", "startDate": "2025-06-01", "endDate": "2025-06-01"}
				""").andExpect(status().isCreated());
	}

	@Test
	void enRollUtanNamnEllerStartdatumAvvisas() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "1985-06-15");

		perform(post("/api/v1/members/" + id + "/roles"), admin, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'startDate')]").exists());
	}

	@Test
	void rollForOkandMedlemGer404() throws Exception {
		perform(post("/api/v1/members/" + OKAND_ID + "/roles"), admin, """
				{"title": "Kassör", "startDate": "2025-01-01"}
				""").andExpect(status().isNotFound());
	}

	// ---- Medlemsavgift

	@Test
	void adminMarkerarAvgiftenSomBetaldOchDetArIdempotent() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");
		int ar = LocalDate.now(clock).getYear();

		perform(put("/api/v1/members/" + id + "/fifs-fees/" + ar), admin, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.year").value(ar))
			.andExpect(jsonPath("$.paidOn").value(LocalDate.now(clock).toString()));
		perform(put("/api/v1/members/" + id + "/fifs-fees/" + ar), admin, null).andExpect(status().isOk());

		perform(get("/api/v1/members/" + id), admin, null).andExpect(jsonPath("$.fifsFees.length()").value(1));
	}

	@Test
	void ettRedanBetaltArBehallerDetForstaDatumet() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");
		perform(put("/api/v1/members/" + id + "/fifs-fees/2025"), admin, null).andExpect(status().isOk());
		jdbc.sql("UPDATE member_fifs_fee SET paid_on = '2025-02-03'").update();

		perform(put("/api/v1/members/" + id + "/fifs-fees/2025"), admin, null).andExpect(status().isOk())
			.andExpect(jsonPath("$.paidOn").value("2025-02-03"));
	}

	@Test
	void avgifterForFlerArListasIArsordning() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");
		perform(put("/api/v1/members/" + id + "/fifs-fees/2025"), admin, null).andExpect(status().isOk());
		perform(put("/api/v1/members/" + id + "/fifs-fees/2024"), admin, null).andExpect(status().isOk());

		perform(get("/api/v1/members/" + id), admin, null).andExpect(jsonPath("$.fifsFees[0].year").value(2024))
			.andExpect(jsonPath("$.fifsFees[1].year").value(2025));
	}

	@Test
	void arUtanforDetTillatnaIntervalletAvvisas() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");
		int ar = LocalDate.now(clock).getYear();

		perform(put("/api/v1/members/" + id + "/fifs-fees/1999"), admin, null).andExpect(status().isBadRequest());
		perform(put("/api/v1/members/" + id + "/fifs-fees/" + (ar + 2)), admin, null).andExpect(status().isBadRequest());
		perform(put("/api/v1/members/" + id + "/fifs-fees/" + (ar + 1)), admin, null).andExpect(status().isOk());
		perform(put("/api/v1/members/" + id + "/fifs-fees/2000"), admin, null).andExpect(status().isOk());
	}

	@Test
	void ettArSomInteArEttTalGer400() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(put("/api/v1/members/" + id + "/fifs-fees/nagot"), admin, null).andExpect(status().isBadRequest());
	}

	@Test
	void avgiftForOkandMedlemGer404() throws Exception {
		perform(put("/api/v1/members/" + OKAND_ID + "/fifs-fees/2025"), admin, null).andExpect(status().isNotFound());
	}

	// ---- Behörighet: utan inloggning och som Dokumenterare

	@Test
	void anonymaAnropGer401() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(get("/api/v1/members"), null, null).andExpect(status().isUnauthorized());
		perform(get("/api/v1/members/" + id), null, null).andExpect(status().isUnauthorized());
		perform(post("/api/v1/members"), null, medlem("Prova", "Provsson", "2010-05-01"))
			.andExpect(status().isUnauthorized());
		perform(put("/api/v1/members/" + id), null, medlem("Prova", "Provsson", "2010-05-01"))
			.andExpect(status().isUnauthorized());
		perform(delete("/api/v1/members/" + id), null, null).andExpect(status().isUnauthorized());
		perform(post("/api/v1/members/" + id + "/roles"), null, """
				{"title": "Kassör", "startDate": "2025-01-01"}
				""").andExpect(status().isUnauthorized());
		perform(put("/api/v1/members/" + id + "/fifs-fees/2025"), null, null).andExpect(status().isUnauthorized());
	}

	@Test
	void dokumenterarenNekasAllaAnropUtomListan() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(get("/api/v1/members/" + id), dokumenterare, null).andExpect(status().isForbidden());
		perform(post("/api/v1/members"), dokumenterare, medlem("Ny", "Medlem", "2010-05-01"))
			.andExpect(status().isForbidden());
		perform(put("/api/v1/members/" + id), dokumenterare, medlem("Andrad", "Medlem", "2010-05-01"))
			.andExpect(status().isForbidden());
		perform(delete("/api/v1/members/" + id), dokumenterare, null).andExpect(status().isForbidden());
		perform(post("/api/v1/members/" + id + "/roles"), dokumenterare, """
				{"title": "Kassör", "startDate": "2025-01-01"}
				""").andExpect(status().isForbidden());
		perform(put("/api/v1/members/" + id + "/fifs-fees/2025"), dokumenterare, null).andExpect(status().isForbidden());

		// Inget av det ovan har ändrat något.
		assertThat(antalMedlemmar()).isEqualTo(1);
		assertThat(jdbc.sql("SELECT status FROM member").query(String.class).single()).isEqualTo("ACTIVE");
		assertThat(jdbc.sql("SELECT first_name FROM member").query(String.class).single()).isEqualTo("Prova");
		assertThat(jdbc.sql("SELECT count(*) FROM member_fifs_fee").query(Long.class).single()).isZero();
		assertThat(jdbc.sql("SELECT count(*) FROM member_role").query(Long.class).single()).isZero();
	}

	@Test
	void forbudetKommerForeValideringOchFlerSokning() throws Exception {
		// Fel roll med ogiltig body och okänt id ger 403, inte 400 eller 404.
		perform(post("/api/v1/members"), dokumenterare, "{}").andExpect(status().isForbidden());
		perform(get("/api/v1/members/" + OKAND_ID), dokumenterare, null).andExpect(status().isForbidden());
	}

	@Test
	void anropSomAndrarDataKraverCsrfToken() throws Exception {
		mockMvc
			.perform(post("/api/v1/members").cookie(admin)
				.contentType(MediaType.APPLICATION_JSON)
				.content(medlem("Prova", "Provsson", "2010-05-01")))
			.andExpect(status().isForbidden());

		assertThat(antalMedlemmar()).isZero();
	}

	@Test
	void felSvarArProblemDetail() throws Exception {
		perform(get("/api/v1/members/" + OKAND_ID), admin, null).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.detail").value("Medlemmen finns inte."));
	}

	// ---- Hjälpmetoder

	private ResultActions perform(MockHttpServletRequestBuilder request, Cookie cookie, String body) throws Exception {
		if (cookie != null) {
			request.cookie(cookie);
		}
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON).content(body);
		}
		return mockMvc.perform(request.with(csrfToken()));
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

	private String skapaMedlem(String fornamn, String efternamn, String fodd) throws Exception {
		String svar = perform(post("/api/v1/members"), admin, medlem(fornamn, efternamn, fodd))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return jsonId(svar);
	}

	private String aldersgruppFor(LocalDate fodd) throws Exception {
		String svar = perform(post("/api/v1/members"), admin, medlem("Prova", "Provsson", fodd.toString()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(svar, "$.ageGroup");
	}

	private static String jsonId(String json) {
		return JsonPath.read(json, "$.id");
	}

	private long antalMedlemmar() {
		return jdbc.sql("SELECT count(*) FROM member").query(Long.class).single();
	}

	private static String medlem(String fornamn, String efternamn, String fodd) {
		return """
				{"firstName": "%s", "lastName": "%s", "birthDate": "%s", "homeMunicipality": "Trollhättan"}
				""".formatted(fornamn, efternamn, fodd);
	}

}
