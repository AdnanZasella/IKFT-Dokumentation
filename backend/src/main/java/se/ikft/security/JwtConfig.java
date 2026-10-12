package se.ikft.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtTypeValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Signerar och verifierar inloggningstoken med HS256. Bara den här backenden signerar och verifierar,
 * så en delad nyckel räcker.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
class JwtConfig {

	@Bean
	JwtEncoder jwtEncoder(JwtProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(properties.key()));
	}

	/**
	 * En token måste ha {@code exp}. Ingen tidsavvikelse tillåts, eftersom samma klocka utfärdar och
	 * kontrollerar token.
	 */
	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.key())
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
		timestamps.setClock(clock);
		// setJwtValidator ersätter standardvalideringen, så kontrollen av typ läggs till här igen.
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtTypeValidator.jwt(), timestamps,
				new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull)));
		return decoder;
	}

}
