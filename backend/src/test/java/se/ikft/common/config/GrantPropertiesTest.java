package se.ikft.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class GrantPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

	@Test
	void standardvardenArKommunensAldersgranser() {
		runner.run(context -> {
			GrantProperties properties = context.getBean(GrantProperties.class);
			assertThat(properties.minAge()).isEqualTo(7);
			assertThat(properties.maxAge()).isEqualTo(25);
		});
	}

	@Test
	void gransernaGarAttSattaMedInstallningar() {
		runner.withPropertyValues("ikft.grant.min-age=6", "ikft.grant.max-age=20").run(context -> {
			GrantProperties properties = context.getBean(GrantProperties.class);
			assertThat(properties.minAge()).isEqualTo(6);
			assertThat(properties.maxAge()).isEqualTo(20);
		});
	}

	@Test
	void enLagstaAlderSomArNollStopparUppstarten() {
		runner.withPropertyValues("ikft.grant.min-age=0").run(context -> assertThat(context).hasFailed());
	}

	@Test
	void enHogstaAlderUnderLagstaAlderStopparUppstarten() {
		runner.withPropertyValues("ikft.grant.min-age=10", "ikft.grant.max-age=9")
			.run(context -> assertThat(context).hasFailed());
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(GrantProperties.class)
	static class Config {

	}

}
