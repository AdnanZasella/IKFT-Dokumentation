package se.ikft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import se.ikft.security.AccessTokens;

/**
 * Appen som engångskommando utan webbserver, som vid återställningen av Admin-lösenordet i
 * docs/05. Då behövs ingen JWT-nyckel, så testet startar utan den.
 */
@Import(NonWebStartupTest.DatabaseOnly.class)
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
class NonWebStartupTest {

	@Autowired
	ApplicationContext context;

	@Test
	void starterUtanJwtNyckelOchUtanInloggning() {
		assertThat(context.getBeanNamesForType(AccessTokens.class)).isEmpty();
		assertThat(context.getBeanNamesForType(JwtDecoder.class)).isEmpty();
	}

	/** Bara databasen, inte nyckeln från {@link TestcontainersConfiguration}. */
	@TestConfiguration(proxyBeanMethods = false)
	static class DatabaseOnly {

		@Bean
		@ServiceConnection
		PostgreSQLContainer postgresContainer() {
			return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
		}

	}

}
