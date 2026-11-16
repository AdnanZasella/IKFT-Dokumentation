package se.ikft.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.util.List;
import java.util.UUID;

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
import org.springframework.transaction.IllegalTransactionStateException;

import se.ikft.TestcontainersConfiguration;
import se.ikft.security.AccessTokens;

/**
 * Ändringsloggen mot en riktig Postgres: vad som loggas när medlemsregistret ändras, vad som inte loggas
 * och vem som får läsa loggen.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuditIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	private static final String OKAND_ID = "00000000-0000-0000-0000-00000000dead";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	AuditService auditService;

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

	// ---- Vad som loggas

	@Test
	void attSkapaEnMedlemLoggasSomCreatedMedVardenForBidragsfalten() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		historik(id).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].action").value("CREATED"))
			.andExpect(jsonPath("$[0].entityType").value("MEMBER"))
			.andExpect(jsonPath("$[0].entityId").value(id))
			.andExpect(jsonPath("$[0].accountRole").value("ADMIN"))
			.andExpect(jsonPath("$[0].createdAt").isNotEmpty())
			.andExpect(jsonPath("$[0].changes[?(@.field == 'birthDate')].after").value("2010-05-01"))
			.andExpect(jsonPath("$[0].changes[?(@.field == 'homeMunicipality')].after").value("Trollhättan"))
			.andExpect(jsonPath("$[0].changes[?(@.field == 'status')].after").value("ACTIVE"));
	}

	@Test
	void enUppdateringLoggarBaraDeFaltSomAndrats() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(put("/api/v1/members/" + id), admin, """
				{"firstName": "Prova", "lastName": "Provsson", "birthDate": "2008-03-02",
				 "homeMunicipality": "Trollhättan"}
				""").andExpect(status().isOk());

		historik(id).andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].action").value("UPDATED"))
			.andExpect(jsonPath("$[0].changes.length()").value(1))
			.andExpect(jsonPath("$[0].changes[0].field").value("birthDate"))
			.andExpect(jsonPath("$[0].changes[0].before").value("2010-05-01"))
			.andExpect(jsonPath("$[0].changes[0].after").value("2008-03-02"));
	}

	@Test
	void namnOchAdressLoggasUtanVardenOchSparasInteIDatabasen() throws Exception {
		String id = skapaMedlem("Gammalnamn", "Gammalsson", "2010-05-01");

		perform(put("/api/v1/members/" + id), admin, """
				{"firstName": "Nyttnamn", "lastName": "Nysson", "birthDate": "2010-05-01",
				 "address": "Hemlig gata 7", "homeMunicipality": "Trollhättan"}
				""").andExpect(status().isOk());

		String svar = historik(id).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].changes[?(@.field == 'firstName')]").exists())
			.andExpect(jsonPath("$[0].changes[?(@.field == 'address')]").exists())
			.andReturn()
			.getResponse()
			.getContentAsString();
		// Varken de gamla eller de nya namnen eller adressen finns i svaret eller i tabellerna.
		for (String hemlig : List.of("Gammalnamn", "Gammalsson", "Nyttnamn", "Nysson", "Hemlig gata 7")) {
			assertThat(svar).doesNotContain(hemlig);
		}
		List<String> sparade = jdbc.sql("""
				SELECT coalesce(before_value, '') || '|' || coalesce(after_value, '') FROM audit_log_change
				""").query(String.class).list();
		assertThat(sparade).noneMatch(rad -> rad.contains("namn") || rad.contains("sson") || rad.contains("gata"));
	}

	@Test
	void enUppdateringUtanAndringarLoggasInte() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(put("/api/v1/members/" + id), admin, medlem("Prova", "Provsson", "2010-05-01"))
			.andExpect(status().isOk());

		historik(id).andExpect(jsonPath("$.length()").value(1));
	}

	@Test
	void attInaktiveraLoggasMedStatusFranOchTillOchInteTvaGanger() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(delete("/api/v1/members/" + id), admin, null).andExpect(status().isNoContent());
		perform(delete("/api/v1/members/" + id), admin, null).andExpect(status().isNoContent());

		historik(id).andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].action").value("DEACTIVATED"))
			.andExpect(jsonPath("$[0].changes[0].field").value("status"))
			.andExpect(jsonPath("$[0].changes[0].before").value("ACTIVE"))
			.andExpect(jsonPath("$[0].changes[0].after").value("INACTIVE"));
	}

	@Test
	void enAterAktiveringViaPutLoggasMedStatus() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");
		perform(delete("/api/v1/members/" + id), admin, null).andExpect(status().isNoContent());

		perform(put("/api/v1/members/" + id), admin, """
				{"firstName": "Prova", "lastName": "Provsson", "birthDate": "2010-05-01",
				 "homeMunicipality": "Trollhättan", "status": "ACTIVE"}
				""").andExpect(status().isOk());

		historik(id).andExpect(jsonPath("$[0].action").value("UPDATED"))
			.andExpect(jsonPath("$[0].changes[0].field").value("status"))
			.andExpect(jsonPath("$[0].changes[0].before").value("INACTIVE"))
			.andExpect(jsonPath("$[0].changes[0].after").value("ACTIVE"));
	}

	@Test
	void enRollLoggasMedNamnOchPeriod() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "1985-06-15");

		perform(post("/api/v1/members/" + id + "/roles"), admin, """
				{"title": "Kassör", "startDate": "2025-01-01", "endDate": "2025-12-31"}
				""").andExpect(status().isCreated());

		historik(id).andExpect(jsonPath("$[0].action").value("UPDATED"))
			.andExpect(jsonPath("$[0].changes[0].field").value("role"))
			.andExpect(jsonPath("$[0].changes[0].after").value("Kassör 2025-01-01–2025-12-31"));
	}

	@Test
	void moskeavgiftenLoggasEnGangPerArOchInteVidUpprepning() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "1985-06-15");

		perform(put("/api/v1/members/" + id + "/fifs-fees/2025"), admin, null).andExpect(status().isOk());
		perform(put("/api/v1/members/" + id + "/fifs-fees/2025"), admin, null).andExpect(status().isOk());

		historik(id).andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].changes[0].field").value("fifsFee"))
			.andExpect(jsonPath("$[0].changes[0].after").value("2025"));
	}

	@Test
	void ettMisslyckatAnropLoggarInget() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(put("/api/v1/members/" + id), admin, medlem("", "Provsson", "2010-05-01"))
			.andExpect(status().isBadRequest());
		perform(post("/api/v1/members/" + id + "/roles"), admin, """
				{"title": "Kassör", "startDate": "2025-06-01", "endDate": "2025-05-01"}
				""").andExpect(status().isBadRequest());
		perform(put("/api/v1/members/" + id + "/fifs-fees/1999"), admin, null).andExpect(status().isBadRequest());
		perform(post("/api/v1/members"), admin, medlem("Prova", "Provsson", "2999-01-01"))
			.andExpect(status().isBadRequest());

		historik(id).andExpect(jsonPath("$.length()").value(1));
		assertThat(jdbc.sql("SELECT count(*) FROM audit_log").query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void loggenVisarInnehavarensNamn() throws Exception {
		jdbc.sql("""
				INSERT INTO member (id, first_name, last_name, birth_date, home_municipality)
				VALUES ('00000000-0000-0000-0000-000000000002', 'Dokumenterande', 'Provsson', '1985-06-15', 'Trollhättan')
				""").update();
		jdbc.sql("""
				UPDATE user_account SET holder_member_id = '00000000-0000-0000-0000-000000000002'
				WHERE username = 'admin'
				""").update();

		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		historik(id).andExpect(jsonPath("$[0].accountRole").value("ADMIN"))
			.andExpect(jsonPath("$[0].holderName").value("Dokumenterande Provsson"));
	}

	@Test
	void senasteAndringenKommerForst() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");
		perform(put("/api/v1/members/" + id + "/fifs-fees/2024"), admin, null).andExpect(status().isOk());
		perform(put("/api/v1/members/" + id + "/fifs-fees/2025"), admin, null).andExpect(status().isOk());

		historik(id).andExpect(jsonPath("$.length()").value(3))
			.andExpect(jsonPath("$[0].changes[0].after").value("2025"))
			.andExpect(jsonPath("$[1].changes[0].after").value("2024"))
			.andExpect(jsonPath("$[2].action").value("CREATED"));
	}

	@Test
	void loggenAvserBaraDenEfterfragadePosten() throws Exception {
		String forsta = skapaMedlem("Forsta", "Medlem", "2010-05-01");
		String andra = skapaMedlem("Andra", "Medlem", "2010-05-01");
		perform(delete("/api/v1/members/" + andra), admin, null).andExpect(status().isNoContent());

		historik(forsta).andExpect(jsonPath("$.length()").value(1));
		historik(andra).andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void enPostUtanLoggGerTomLista() throws Exception {
		historik(OKAND_ID).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	// ---- Behörighet och indata

	@Test
	void bataraAdminKommerAtLoggen() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(get("/api/v1/audit-log?entity=MEMBER&id=" + id), null, null).andExpect(status().isUnauthorized());
		perform(get("/api/v1/audit-log?entity=MEMBER&id=" + id), dokumenterare, null)
			.andExpect(status().isForbidden());
	}

	@Test
	void saknadeEllerOgiltigaParametrarGer400() throws Exception {
		perform(get("/api/v1/audit-log"), admin, null).andExpect(status().isBadRequest());
		perform(get("/api/v1/audit-log?entity=MEMBER"), admin, null).andExpect(status().isBadRequest());
		perform(get("/api/v1/audit-log?id=" + OKAND_ID), admin, null).andExpect(status().isBadRequest());
		perform(get("/api/v1/audit-log?entity=NAGOT&id=" + OKAND_ID), admin, null)
			.andExpect(status().isBadRequest());
		perform(get("/api/v1/audit-log?entity=MEMBER&id=inte-ett-uuid"), admin, null)
			.andExpect(status().isBadRequest());
	}

	@Test
	void loggenGarInteAttSkrivaViaApiet() throws Exception {
		String id = skapaMedlem("Prova", "Provsson", "2010-05-01");

		perform(post("/api/v1/audit-log"), admin, "{}").andExpect(status().is4xxClientError());
		perform(put("/api/v1/audit-log"), admin, "{}").andExpect(status().is4xxClientError());
		perform(delete("/api/v1/audit-log?entity=MEMBER&id=" + id), admin, null)
			.andExpect(status().is4xxClientError());

		historik(id).andExpect(jsonPath("$.length()").value(1));
	}

	// ---- Själva loggtjänsten

	@Test
	void enAndringKanInteLoggasUtanPagaendeTransaktion() {
		assertThatThrownBy(() -> auditService.record(AuditEntity.MEMBER, UUID.randomUUID(), AuditAction.CREATED,
				List.of(FieldChange.named("firstName")))).isInstanceOf(IllegalTransactionStateException.class);

		assertThat(jdbc.sql("SELECT count(*) FROM audit_log").query(Long.class).single()).isZero();
	}

	@Test
	void personnummerFinnsInteIBeskrivningenAvFaltenISchemat() {
		long antal = jdbc.sql("""
				SELECT count(*) FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name LIKE 'audit_log%'
				  AND (column_name ILIKE '%personnummer%' OR column_name ILIKE '%ssn%')
				""").query(Long.class).single();

		assertThat(antal).isZero();
	}

	// ---- Hjälpmetoder

	private ResultActions historik(String id) throws Exception {
		return perform(get("/api/v1/audit-log?entity=MEMBER&id=" + id), admin, null);
	}

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
		return JsonPath.read(svar, "$.id");
	}

	private static String medlem(String fornamn, String efternamn, String fodd) {
		return """
				{"firstName": "%s", "lastName": "%s", "birthDate": "%s", "homeMunicipality": "Trollhättan"}
				""".formatted(fornamn, efternamn, fodd);
	}

}
