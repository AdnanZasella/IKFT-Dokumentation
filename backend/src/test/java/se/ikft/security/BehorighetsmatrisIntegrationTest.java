package se.ikft.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static se.ikft.security.TestCsrf.csrfToken;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import se.ikft.TestcontainersConfiguration;

/**
 * Behörighetsmatrisen för Fas 1 (#20): varje endpoint under {@code /api/v1} anropas som anonym, Dokumenterare och
 * Admin, och svaret ska vara det matrisen säger: 401, 403 eller tillåtet.
 * <p>
 * Tillåtet betyder att säkerhetskedjan släpper igenom anropet, alltså att svaret varken är 401 eller 403 och inte
 * ett serverfel. Anropen skickar tom kropp och slumpade id:n, och rollkontrollen ligger före validering och
 * uppslag, så ett tillåtet anrop ger 400 eller 404 och ändrar ingenting. Vad endpointen gör när anropet lyckas
 * testas i funktionens egna tester.
 * <p>
 * {@link #varjeEndpointFinnsIMatrisen()} jämför matrisen med vad Spring faktiskt har registrerat, så att en ny
 * endpoint utan rad här stoppar bygget.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BehorighetsmatrisIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	private static final String API = "/api/v1";

	enum Roll {

		ANONYM, DOKUMENTERARE, ADMIN

	}

	/** Det anropet ska ge. {@code TILLATEN} är allt utom 401, 403 och serverfel. */
	enum Svar {

		TILLATEN, OAUTENTISERAD, FORBJUDEN

	}

	record Endpoint(String metod, String path, Svar anonym, Svar dokumenterare, Svar admin) {

		String nyckel() {
			return metod + " " + path;
		}

		Svar svarFor(Roll roll) {
			return switch (roll) {
				case ANONYM -> anonym;
				case DOKUMENTERARE -> dokumenterare;
				case ADMIN -> admin;
			};
		}

		@Override
		public String toString() {
			return nyckel();
		}

	}

	/** Öppen: säkerhetskedjan kräver ingen inloggning. */
	private static Endpoint oppen(String metod, String path) {
		return new Endpoint(metod, API + path, Svar.TILLATEN, Svar.TILLATEN, Svar.TILLATEN);
	}

	/** Kräver inloggning, men vilken roll som helst får anropa. */
	private static Endpoint inloggad(String metod, String path) {
		return new Endpoint(metod, API + path, Svar.OAUTENTISERAD, Svar.TILLATEN, Svar.TILLATEN);
	}

	/** Bara Admin. */
	private static Endpoint adminEnbart(String metod, String path) {
		return new Endpoint(metod, API + path, Svar.OAUTENTISERAD, Svar.FORBJUDEN, Svar.TILLATEN);
	}

	/**
	 * Matrisen. En ny endpoint läggs till här i samma ändring som den skapas, och i tabellerna i
	 * docs/04-domanmodell-och-api.md.
	 */
	static final List<Endpoint> MATRIS = List.of(
			// Autentisering. Login, logout och set-password är öppna. Refresh är öppen i säkerhetskedjan, men
			// anonym saknar förnyelsecookie och får 401 av endpointen själv.
			oppen("POST", "/auth/login"),
			new Endpoint("POST", API + "/auth/refresh", Svar.OAUTENTISERAD, Svar.TILLATEN, Svar.TILLATEN),
			oppen("POST", "/auth/logout"),
			oppen("POST", "/auth/set-password"),
			inloggad("GET", "/auth/me"),
			inloggad("POST", "/auth/change-password"),
			// Konton
			adminEnbart("POST", "/accounts/{id}/setup-code"),
			// Medlemmar. Dokumenterare får bara lista.
			inloggad("GET", "/members"),
			adminEnbart("POST", "/members"),
			adminEnbart("GET", "/members/{id}"),
			adminEnbart("PUT", "/members/{id}"),
			adminEnbart("DELETE", "/members/{id}"),
			adminEnbart("POST", "/members/{id}/roles"),
			adminEnbart("PUT", "/members/{id}/fifs-fees/{year}"),
			// Dokumenteraruppdrag. Dokumenterare får bara se det aktuella.
			inloggad("GET", "/documenter-assignment/current"),
			adminEnbart("POST", "/documenter-assignment"),
			adminEnbart("POST", "/documenter-assignment/temporary"),
			adminEnbart("DELETE", "/documenter-assignment/temporary"),
			// Ändringslogg
			adminEnbart("GET", "/audit-log"));

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping handlerMapping;

	private Cookie[] admin;

	private Cookie[] dokumenterare;

	@BeforeAll
	void forbered() throws Exception {
		// Ett kvarlämnat tillfälligt uppdrag skulle avslutas av Admins DELETE, och en kvarlämnad flagga skulle ge
		// Dokumenterare 403 överallt.
		jdbc.sql("DELETE FROM documenter_assignment").update();
		jdbc.sql("UPDATE user_account SET must_change_password = false").update();
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
		admin = loggaIn("admin");
		dokumenterare = loggaIn("dokumenterare");
	}

	@AfterAll
	void stada() {
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0, holder_member_id = NULL").update();
	}

	static Stream<Arguments> kombinationer() {
		return MATRIS.stream().flatMap(endpoint -> Stream.of(Roll.values()).map(roll -> Arguments.of(endpoint, roll)));
	}

	@ParameterizedTest(name = "{0} som {1}")
	@MethodSource("kombinationer")
	void endpointenGerRattSvarForRollen(Endpoint endpoint, Roll roll) throws Exception {
		Svar forvantat = endpoint.svarFor(roll);

		MockHttpServletResponse svar = mockMvc.perform(anrop(endpoint, roll)).andReturn().getResponse();

		switch (forvantat) {
			case OAUTENTISERAD -> assertProblem(svar, 401, endpoint, roll);
			case FORBJUDEN -> assertProblem(svar, 403, endpoint, roll);
			case TILLATEN -> assertThat(svar.getStatus()).as("%s som %s ska släppas igenom", endpoint, roll)
				.isNotIn(401, 403)
				.isLessThan(500);
		}
	}

	@Test
	void varjeEndpointFinnsIMatrisen() {
		Set<String> iKoden = endpointsIKoden();
		Set<String> iMatrisen = new TreeSet<>();
		MATRIS.forEach(endpoint -> iMatrisen.add(endpoint.nyckel()));

		assertThat(iKoden).as("endpoints under %s som hittats i koden", API).isNotEmpty();
		Set<String> saknas = new TreeSet<>(iKoden);
		saknas.removeAll(iMatrisen);
		assertThat(saknas).as("endpoints i koden som saknas i behörighetsmatrisen (lägg till en rad)").isEmpty();
		Set<String> overflodiga = new TreeSet<>(iMatrisen);
		overflodiga.removeAll(iKoden);
		assertThat(overflodiga).as("rader i matrisen utan endpoint i koden (ta bort raden)").isEmpty();
	}

	@Test
	void matrisenHarInteSammaEndpointTvaGanger() {
		assertThat(MATRIS.stream().map(Endpoint::nyckel)).doesNotHaveDuplicates();
	}

	// ---- Hjälpmetoder

	/** Alla endpoints under {@code /api/v1} som Spring har registrerat, som {@code METOD path}. */
	private Set<String> endpointsIKoden() {
		Set<String> endpoints = new TreeSet<>();
		for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
			Set<String> paths = Objects.requireNonNull(info.getPathPatternsCondition()).getPatternValues();
			Set<RequestMethod> metoder = info.getMethodsCondition().getMethods();
			for (String path : paths) {
				if (!path.startsWith(API + "/")) {
					continue;
				}
				// En endpoint utan angiven metod svarar på alla, och går inte att placera i matrisen.
				assertThat(metoder).as("%s måste ange HTTP-metod", path).isNotEmpty();
				metoder.forEach(metod -> endpoints.add(metod + " " + path));
			}
		}
		return endpoints;
	}

	private MockHttpServletRequestBuilder anrop(Endpoint endpoint, Roll roll) {
		String path = endpoint.path().replace("{year}", "2026").replaceAll("\\{[^/}]+}", UUID.randomUUID().toString());
		HttpMethod metod = HttpMethod.valueOf(endpoint.metod());
		MockHttpServletRequestBuilder builder = request(metod, path);
		if (!HttpMethod.GET.equals(metod)) {
			builder.with(csrfToken());
			if (!HttpMethod.DELETE.equals(metod)) {
				builder.contentType(MediaType.APPLICATION_JSON).content("{}");
			}
		}
		return switch (roll) {
			case ANONYM -> builder;
			case DOKUMENTERARE -> builder.cookie(dokumenterare);
			case ADMIN -> builder.cookie(admin);
		};
	}

	private static void assertProblem(MockHttpServletResponse svar, int forvantadStatus, Endpoint endpoint, Roll roll)
			throws Exception {
		assertThat(svar.getStatus()).as("%s som %s", endpoint, roll).isEqualTo(forvantadStatus);
		// Alla felsvar är ProblemDetail, även 401 och 403.
		assertThat(svar.getContentType()).as("%s som %s", endpoint, roll).isNotNull();
		assertThat(MediaType.parseMediaType(svar.getContentType()).isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.as("%s som %s ger ProblemDetail", endpoint, roll)
			.isTrue();
	}

	/** Inloggningens båda cookies: åtkomsttoken och förnyelsetoken. */
	private Cookie[] loggaIn(String username) throws Exception {
		MockHttpServletResponse svar = mockMvc
			.perform(request(HttpMethod.POST, API + "/auth/login").with(csrfToken())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "%s", "password": "%s"}
						""".formatted(username, LOSENORD)))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andReturn()
			.getResponse();
		return new Cookie[] { svar.getCookie(AccessTokens.COOKIE_NAME), svar.getCookie(RefreshTokens.COOKIE_NAME) };
	}

}
