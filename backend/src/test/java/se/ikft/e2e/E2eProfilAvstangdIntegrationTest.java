package se.ikft.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import se.ikft.TestcontainersConfiguration;

/**
 * Hjälpklassen för E2E sätter kända lösenord, så den får aldrig finnas i appen när profilen {@code e2e} inte är
 * aktiv. Kontexten har samma uppsättning som de flesta integrationstester och delas med dem.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class E2eProfilAvstangdIntegrationTest {

	@Autowired
	ApplicationContext context;

	@Autowired
	Environment environment;

	@Test
	void hjalpklassenFinnsInteIVanligProfil() {
		assertThat(environment.getActiveProfiles()).doesNotContain("e2e");

		assertThat(context.getBeansOfType(E2eDataInitializer.class)).isEmpty();
	}

}
