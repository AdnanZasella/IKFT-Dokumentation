package se.ikft.security;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Signerar och verifierar inloggningstoken med HS256. Bara den här backenden signerar och verifierar,
 * så en delad nyckel räcker.
 */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

	@Bean
	JwtEncoder jwtEncoder(JwtProperties properties) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(properties.key()));
	}

	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.key())
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator();
		timestamps.setClock(clock);
		decoder.setJwtValidator(timestamps);
		return decoder;
	}

}
