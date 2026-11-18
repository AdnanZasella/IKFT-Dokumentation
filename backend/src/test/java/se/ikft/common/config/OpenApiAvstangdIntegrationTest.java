package se.ikft.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import se.ikft.TestcontainersConfiguration;

/**
 * OpenAPI-beskrivningen ska inte finnas i drift (#46). Beskrivningen listar varje endpoint, och den behövs bara för
 * att skapa kontraktet till admin-appen, se {@link OpenApiKontraktIntegrationTest}. Testerna kör appen med
 * standardinställningar.
 * <p>
 * Säkerhetsfiltren är avstängda. Säkerhetskedjan nekar redan allt utanför {@code /api/v1}, så med filtren på skulle
 * ett anrop få 401 både när beskrivningen är på och när den är av. Utan filtren är skillnaden synlig: 200 när den är
 * på och 404 när den är av.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class OpenApiAvstangdIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void beskrivningenArInteRegistreradMedStandardinstallningar() {
		Set<String> paths = new TreeSet<>();
		for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
			paths.addAll(Objects.requireNonNull(info.getPathPatternsCondition()).getPatternValues());
		}

		assertThat(paths).noneMatch(path -> path.startsWith("/v3/api-docs"));
	}

	@Test
	void beskrivningenSvararInteMedStandardinstallningar() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
	}

}
