package se.ikft.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Felsvaren som säkerhetskedjan skriver själv (401, 403 och 403 med {@code mustChangePassword}) ska stämma med
 * {@code ProblemDetail}-schemat i den incheckade OpenAPI-beskrivningen, annars blir felkroppen i admin-appens klient fel
 * typad. Klienten skiljer lösenordsbytet från ett vanligt 403 på just {@code mustChangePassword}. Ett 400 för ogiltiga
 * fält jämförs i {@code OpenApiKontraktIntegrationTest}.
 */
class ProblemDetailSchemaTest {

	/** Backendens tester körs med {@code backend/} som arbetsmapp. */
	private static final Path BESKRIVNING = Path.of("..", "admin", "src", "api", "openapi.json");

	private static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final ProblemDetailSecurityHandler handler = new ProblemDetailSecurityHandler();

	@Test
	void ett401HarBaraEgenskaperSomSchemaetBeskriver() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("saknas"));

		JsonNode kropp = kropp(response);
		assertThat(response.getStatus()).isEqualTo(401);
		assertStammerMedSchemat(kropp);
		assertThat(kropp.path("status").asInt()).isEqualTo(401);
		assertThat(kropp.has("mustChangePassword")).isFalse();
	}

	@Test
	void ettVanligt403HarBaraEgenskaperSomSchemaetBeskriverOchIngenMustChangePassword() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("nekad"));

		JsonNode kropp = kropp(response);
		assertThat(response.getStatus()).isEqualTo(403);
		assertStammerMedSchemat(kropp);
		assertThat(kropp.has("mustChangePassword")).isFalse();
	}

	@Test
	void ett403FranLosenordsbytetHarMustChangePasswordSomSchemaetBeskriver() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.passwordChangeRequired(response);

		JsonNode kropp = kropp(response);
		assertThat(response.getStatus()).isEqualTo(403);
		assertStammerMedSchemat(kropp);
		assertThat(kropp.path("mustChangePassword").asBoolean()).isTrue();
		assertThat(schema().path("properties").path("mustChangePassword").path("type").asString())
			.isEqualTo("boolean");
	}

	private static JsonNode kropp(MockHttpServletResponse response) throws Exception {
		assertThat(response.getContentType()).startsWith("application/problem+json");
		return MAPPER.readTree(response.getContentAsString(StandardCharsets.UTF_8));
	}

	private static void assertStammerMedSchemat(JsonNode kropp) throws Exception {
		JsonNode schema = schema();
		assertThat(namn(kropp)).as("egenskaper i svaret").isSubsetOf(namn(schema.path("properties")));
		Set<String> obligatoriska = new TreeSet<>();
		schema.path("required").forEach(nod -> obligatoriska.add(nod.asString()));
		assertThat(namn(kropp)).as("obligatoriska egenskaper").containsAll(obligatoriska);
	}

	private static JsonNode schema() throws Exception {
		assertThat(BESKRIVNING).as("%s finns inte", BESKRIVNING).exists();
		return MAPPER.readTree(Files.readString(BESKRIVNING, StandardCharsets.UTF_8))
			.path("components")
			.path("schemas")
			.path("ProblemDetail");
	}

	private static Set<String> namn(JsonNode objekt) {
		Set<String> namn = new TreeSet<>();
		objekt.propertyNames().forEach(namn::add);
		return namn;
	}

}
