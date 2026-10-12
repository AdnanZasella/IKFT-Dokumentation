package se.ikft.security;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;

import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import se.ikft.TestcontainersConfiguration;
import se.ikft.account.UserAccountRepository;

/**
 * Att anrop autentiseras med token i cookien, och att token som inte längre ska gälla ger 401.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationIntegrationTest {

	private static final String LOSENORD = "ett-langt-losenord";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JwtEncoder jwtEncoder;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	Clock clock;

	@BeforeEach
	void forbered() {
		aterstall();
		jdbc.sql("UPDATE user_account SET password_hash = ?").param(passwordEncoder.encode(LOSENORD)).update();
	}

	@AfterEach
	void aterstall() {
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0").update();
	}

	@Test
	void giltigCookieGarIgenom() throws Exception {
		me(login("admin")).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void utanCookieGer401() throws Exception {
		mockMvc.perform(get("/api/v1/auth/me")).andExpect(problem401());
	}

	@Test
	void utgangenTokenGer401() throws Exception {
		Instant tidigare = clock.instant().minusSeconds(3600);
		String token = signera(jwtEncoder, claims("admin", 0, tidigare, tidigare.plusSeconds(60)));

		me(token).andExpect(problem401());
	}

	@Test
	void tokenSomNyssGattUtGer401() throws Exception {
		// Ingen tidsavvikelse tillåts.
		Instant nu = clock.instant();
		String token = signera(jwtEncoder, claims("admin", 0, nu.minusSeconds(600), nu.minusSeconds(1)));

		me(token).andExpect(problem401());
	}

	@Test
	void tokenUtanUtgangstidGer401() throws Exception {
		Instant nu = clock.instant();
		String token = signera(jwtEncoder, claims("admin", 0, nu, nu.plusSeconds(600), b -> b.claims(c -> c
			.remove(JwtClaimNames.EXP))));

		me(token).andExpect(problem401());
	}

	@Test
	void ogiltigCookieHindrarInteDetPublika() throws Exception {
		Cookie ogiltig = new Cookie(AccessTokens.COOKIE_NAME, "inte.en.token");

		mockMvc.perform(get("/actuator/health").cookie(ogiltig)).andExpect(status().isOk());
		// Ingen publik endpoint finns än, så 404 visar att begäran släpptes igenom.
		mockMvc.perform(get("/api/v1/public/finns-inte").cookie(ogiltig)).andExpect(status().isNotFound());
	}

	@Test
	void manipuleradTokenGer401() throws Exception {
		String[] delar = login("dokumenterare").split("\\.");
		String payload = new String(Base64.getUrlDecoder().decode(delar[1]), UTF_8).replace("DOCUMENTER", "ADMIN");
		String manipulerad = delar[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(UTF_8))
				+ "." + delar[2];

		me(manipulerad).andExpect(problem401());
	}

	@Test
	void tokenSigneradMedEnAnnanNyckelGer401() throws Exception {
		byte[] annanNyckel = new byte[32];
		annanNyckel[0] = 1;
		JwtEncoder annan = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(annanNyckel, "HmacSHA256")));
		Instant nu = clock.instant();

		me(signera(annan, claims("admin", 0, nu, nu.plusSeconds(600)))).andExpect(problem401());
	}

	@Test
	void tokenMedGammalTokenversionGer401() throws Exception {
		String token = login("dokumenterare");
		jdbc.sql("UPDATE user_account SET token_version = token_version + 1 WHERE username = 'dokumenterare'")
			.update();

		me(token).andExpect(problem401());
	}

	@Test
	void tokenUtanTokenversionGer401() throws Exception {
		Instant nu = clock.instant();
		String token = signera(jwtEncoder, claims("admin", 0, nu, nu.plusSeconds(600), b -> b.claims(c -> c
			.remove(AccessTokens.TOKEN_VERSION_CLAIM))));

		me(token).andExpect(problem401());
	}

	@Test
	void tokenForEttKontoSomInteFinnsGer401() throws Exception {
		Instant nu = clock.instant();
		String token = signera(jwtEncoder, claims("admin", 0, nu, nu.plusSeconds(600),
				b -> b.subject(UUID.randomUUID().toString())));

		me(token).andExpect(problem401());
	}

	@Test
	void tokenIAuthorizationHuvudetIgnoreras() throws Exception {
		String token = login("admin");

		mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(problem401());
	}

	@Test
	void ogiltigCookieHindrarInteInloggningen() throws Exception {
		mockMvc
			.perform(post("/api/v1/auth/login").cookie(new Cookie(AccessTokens.COOKIE_NAME, "inte.en.token"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username": "admin", "password": "%s"}
						""".formatted(LOSENORD)))
			.andExpect(status().isOk());
	}

	private String login(String username) throws Exception {
		return mockMvc
			.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
					{"username": "%s", "password": "%s"}
					""".formatted(username, LOSENORD)))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getCookie(AccessTokens.COOKIE_NAME)
			.getValue();
	}

	private ResultActions me(String token) throws Exception {
		return mockMvc.perform(get("/api/v1/auth/me").cookie(new Cookie(AccessTokens.COOKIE_NAME, token)));
	}

	private JwtClaimsSet claims(String username, int tokenVersion, Instant issuedAt, Instant expiresAt) {
		return claims(username, tokenVersion, issuedAt, expiresAt, builder -> {
		});
	}

	private JwtClaimsSet claims(String username, int tokenVersion, Instant issuedAt, Instant expiresAt,
			Consumer<JwtClaimsSet.Builder> andra) {
		var konto = accounts.findByUsername(username).orElseThrow();
		JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
			.subject(konto.getId().toString())
			.claim(AccessTokens.ROLE_CLAIM, konto.getRole().name())
			.claim(AccessTokens.TOKEN_VERSION_CLAIM, tokenVersion)
			.issuedAt(issuedAt)
			.expiresAt(expiresAt);
		andra.accept(builder);
		return builder.build();
	}

	private static String signera(JwtEncoder encoder, JwtClaimsSet claims) {
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}

	private static ResultMatcher problem401() {
		return result -> {
			status().isUnauthorized().match(result);
			content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON).match(result);
			jsonPath("$.status").value(401).match(result);
		};
	}

}
