package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AuthPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(Config.class);

	@Test
	void standardvardenGaller() {
		runner.run(context -> {
			AuthProperties properties = context.getBean(AuthProperties.class);
			assertThat(properties.setupCodeValidity()).isEqualTo(Duration.ofHours(24));
			assertThat(properties.passwordMinLength()).isEqualTo(12);
			assertThat(properties.resetAdminPassword()).isFalse();
			assertThat(properties.loginMaxAttempts()).isEqualTo(5);
			assertThat(properties.loginLockout()).isEqualTo(Duration.ofMinutes(15));
		});
	}

	@Test
	void konfigureradeSparrvardenGaller() {
		runner.withPropertyValues("ikft.auth.login-max-attempts=3", "ikft.auth.login-lockout=1h").run(context -> {
			AuthProperties properties = context.getBean(AuthProperties.class);
			assertThat(properties.loginMaxAttempts()).isEqualTo(3);
			assertThat(properties.loginLockout()).isEqualTo(Duration.ofHours(1));
		});
	}

	@Test
	void nollForsokStopparUppstarten() {
		runner.withPropertyValues("ikft.auth.login-max-attempts=0")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void sparrtidUnderEnMinutStopparUppstarten() {
		runner.withPropertyValues("ikft.auth.login-lockout=30s").run(context -> assertThat(context).hasFailed());
	}

	@Test
	void giltighetstidSomArNollStopparUppstarten() {
		runner.withPropertyValues("ikft.auth.setup-code-validity=0s")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void negativGiltighetstidStopparUppstarten() {
		runner.withPropertyValues("ikft.auth.setup-code-validity=-72h")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void minstaLosenordslangdSomArNollStopparUppstarten() {
		runner.withPropertyValues("ikft.auth.password-min-length=0")
			.run(context -> assertThat(context).hasFailed());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(AuthProperties.class)
	static class Config {

	}

}
