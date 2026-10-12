package se.ikft.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JwtPropertiesTest {

	private static final String NYCKEL_32_BYTE = Base64.getEncoder().encodeToString(new byte[32]);

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(Config.class);

	@Test
	void standardvardenGaller() {
		runner.withPropertyValues("ikft.security.jwt.secret=" + NYCKEL_32_BYTE).run(context -> {
			JwtProperties properties = context.getBean(JwtProperties.class);
			assertThat(properties.validity()).isEqualTo(Duration.ofMinutes(15));
			assertThat(properties.key().getEncoded()).hasSize(32);
		});
	}

	@Test
	void toStringVisarInteNyckeln() {
		String nyckel = Base64.getEncoder().encodeToString("en-hemlig-nyckel-pa-minst-32-byte".getBytes());
		JwtProperties properties = new JwtProperties(nyckel, Duration.ofMinutes(15));

		assertThat(properties.toString()).doesNotContain(nyckel).contains("PT15M");
	}

	@Test
	void nyckelMedRadbrytningarGodtas() {
		// Som från openssl rand -base64 64, som bryter raden efter 64 tecken.
		byte[] bytes = new byte[64];
		bytes[63] = 1;
		String radbruten = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(bytes);
		assertThat(radbruten).contains("\n");

		assertThat(new JwtProperties(radbruten, Duration.ofMinutes(15)).key().getEncoded()).isEqualTo(bytes);
	}

	@Test
	void nyckelSomSaknasStopparUppstarten() {
		runner.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void nyckelKortareAn32ByteStopparUppstarten() {
		String kort = Base64.getEncoder().encodeToString(new byte[31]);
		runner.withPropertyValues("ikft.security.jwt.secret=" + kort)
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void nyckelSomInteArBase64StopparUppstarten() {
		runner.withPropertyValues("ikft.security.jwt.secret=inte base64 alls!!")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void giltighetstidUnderEnMinutStopparUppstarten() {
		runner.withPropertyValues("ikft.security.jwt.secret=" + NYCKEL_32_BYTE, "ikft.security.jwt.validity=30s")
			.run(context -> assertThat(context).hasFailed());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(JwtProperties.class)
	static class Config {

	}

}
