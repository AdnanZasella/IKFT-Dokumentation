package se.ikft.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import se.ikft.TestcontainersConfiguration;

/**
 * Kontraktet mellan backend och frontend (#46): OpenAPI-beskrivningen av {@code /api/v1} checkas in som
 * {@code admin/src/api/openapi.json}, och typerna i admin-appen genereras ur den. Testet misslyckas när
 * beskrivningen i koden skiljer sig från den incheckade filen, så att en ändrad endpoint stoppar bygget i stället
 * för att ge en tyst bugg i frontenden.
 * <p>
 * Uppdatera filen med {@code ./mvnw test -Dtest=OpenApiKontraktIntegrationTest -Dopenapi.update=true} och
 * checka in resultatet.
 * <p>
 * Beskrivningen är avstängd i drift. Här slås den på bara för det här testet, och säkerhetsfiltren är avstängda
 * eftersom testet läser beskrivningen och inte prövar behörighet. Att den är stängd i drift prövas av
 * {@link OpenApiAvstangdIntegrationTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc(addFilters = false)
class OpenApiKontraktIntegrationTest {

	private static final String API = "/api/v1";

	/** Backendens tester körs med {@code backend/} som arbetsmapp. */
	private static final Path FIL = Path.of("..", "admin", "src", "api", "openapi.json");

	private static final String UPPDATERA = "openapi.update";

	private static final ObjectMapper MAPPER = JsonMapper.builder()
		.enable(SerializationFeature.INDENT_OUTPUT)
		.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
		.build();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void denIncheckadeBeskrivningenArAktuell() throws Exception {
		String aktuell = beskrivning();

		if (Boolean.getBoolean(UPPDATERA)) {
			// Uppdateringsläget skriver filen och godkänner alltid, så det får aldrig vara på i CI.
			assertThat(System.getenv("CI")).as("-D%s får inte användas i CI", UPPDATERA).isNull();
			Files.createDirectories(FIL.getParent());
			Files.writeString(FIL, aktuell, StandardCharsets.UTF_8);
			return;
		}

		assertThat(FIL).as("%s finns inte. Skapa den med -D%s=true, se klassens beskrivning", FIL, UPPDATERA)
			.exists();
		assertThat(lasFilen()).as("OpenAPI-beskrivningen har ändrats. Kör testet med -D%s=true och checka in %s",
				UPPDATERA, FIL)
			.isEqualTo(aktuell);
	}

	@Test
	void varjeEndpointUnderApiV1FinnsIBeskrivningen() throws Exception {
		JsonNode paths = MAPPER.readTree(beskrivning()).path("paths");
		Set<String> ibeskrivningen = new TreeSet<>();
		paths.properties().forEach(path -> path.getValue()
			.propertyNames()
			.forEach(metod -> ibeskrivningen.add(metod.toUpperCase(Locale.ROOT) + " " + path.getKey())));

		Set<String> ikoden = endpointsIKoden();

		assertThat(ikoden).as("endpoints under %s i koden", API).isNotEmpty();
		assertThat(ibeskrivningen).as("endpoints i OpenAPI-beskrivningen").containsExactlyInAnyOrderElementsOf(ikoden);
	}

	@Test
	void svarsfaltenArObligatoriskaUtomDeNullbara() throws Exception {
		JsonNode scheman = MAPPER.readTree(beskrivning()).path("components").path("schemas");

		JsonNode konto = scheman.path("AccountInfo");
		assertThat(namnen(konto.path("required"))).containsExactlyInAnyOrder("role", "mustChangePassword");
		assertThat(konto.path("properties").has("holderName")).isTrue();

		// Fälten som bara följer med för Admin och fälten som är tomma utan uppdrag är inte obligatoriska.
		assertThat(namnen(scheman.path("CurrentAssignmentResponse").path("required"))).containsExactly("assigned");
	}

	@Test
	void forfragningarKravsInteAvSvarsregeln() throws Exception {
		JsonNode scheman = MAPPER.readTree(beskrivning()).path("components").path("schemas");

		// Kravet kommer från valideringen (@NotNull, @NotBlank), inte från att fältet inte är nullbart.
		assertThat(namnen(scheman.path("MemberRequest").path("required")))
			.containsExactlyInAnyOrder("birthDate", "firstName", "homeMunicipality", "lastName");
	}

	@Test
	void varjeEndpointHarProblemDetailSomFelsvar() throws Exception {
		JsonNode api = MAPPER.readTree(beskrivning());
		JsonNode problem = api.path("components").path("schemas").path("ProblemDetail");

		assertThat(namnen(problem.path("required"))).containsExactly("status");
		assertThat(egenskaper(problem.path("properties"))).containsExactlyInAnyOrder("type", "title", "status", "detail",
				"instance", "errors", "mustChangePassword");

		Set<String> utanFelsvar = new TreeSet<>();
		api.path("paths").properties().forEach(path -> path.getValue().properties().forEach(operation -> {
			JsonNode schema = operation.getValue()
				.path("responses")
				.path("default")
				.path("content")
				.path("application/problem+json")
				.path("schema");
			if (!"#/components/schemas/ProblemDetail".equals(schema.path("$ref").asString())) {
				utanFelsvar.add(operation.getKey().toUpperCase(Locale.ROOT) + " " + path.getKey());
			}
		}));
		assertThat(utanFelsvar).as("endpoints utan ProblemDetail som felsvar").isEmpty();
	}

	/**
	 * Schemat ska stämma med det backenden faktiskt svarar, annars blir felkroppen i klienten fel typad. Ett 400 för
	 * ogiltiga fält är det svar som har flest egenskaper.
	 */
	@Test
	void ettVerkligtFelsvarHarBaraEgenskaperSomSchemaetBeskriver() throws Exception {
		JsonNode problem = MAPPER.readTree(beskrivning()).path("components").path("schemas").path("ProblemDetail");
		String svar = mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		JsonNode kropp = MAPPER.readTree(svar);

		assertThat(egenskaper(kropp)).isSubsetOf(egenskaper(problem.path("properties")));
		assertThat(namnen(problem.path("required"))).isSubsetOf(egenskaper(kropp));
		assertThat(kropp.path("errors")).isNotEmpty();
		kropp.path("errors")
			.forEach(fel -> assertThat(egenskaper(fel))
				.isEqualTo(egenskaper(problem.path("properties").path("errors").path("items").path("properties"))));
	}

	// ---- Hjälpmetoder

	private static Set<String> egenskaper(JsonNode objekt) {
		Set<String> namn = new TreeSet<>();
		objekt.propertyNames().forEach(namn::add);
		return namn;
	}

	private static Set<String> namnen(JsonNode lista) {
		Set<String> namn = new TreeSet<>();
		lista.forEach(nod -> namn.add(nod.asString()));
		return namn;
	}


	/**
	 * Beskrivningen som stabil JSON: nycklarna sorterade, utan {@code servers} (som beror på hur den hämtades) och med
	 * radbrytning sist.
	 */
	private String beskrivning() throws Exception {
		String json = mockMvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(StandardCharsets.UTF_8);
		ObjectNode tree = (ObjectNode) MAPPER.readTree(json);
		tree.remove("servers");
		// Jackson skriver radbrytningar med systemets radslut, så de görs lika på alla system.
		return MAPPER.writeValueAsString(MAPPER.treeToValue(tree, Object.class)).replace("\r\n", "\n") + "\n";
	}

	private static String lasFilen() throws IOException {
		return Files.readString(FIL, StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	/** Alla endpoints under {@code /api/v1} som Spring har registrerat, som {@code METOD path}. */
	private Set<String> endpointsIKoden() {
		Set<String> endpoints = new TreeSet<>();
		for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
			Set<String> paths = Objects.requireNonNull(info.getPathPatternsCondition()).getPatternValues();
			Set<RequestMethod> metoder = info.getMethodsCondition().getMethods();
			for (String path : paths) {
				if (path.startsWith(API + "/")) {
					metoder.forEach(metod -> endpoints.add(metod + " " + path));
				}
			}
		}
		return endpoints;
	}

}
