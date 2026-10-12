package se.ikft.security;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import se.ikft.account.UserAccountRepository;

/**
 * Nekar allt som inte uttryckligen är öppet. 401 och 403 skrivs av {@link ProblemDetailSecurityHandler}
 * så att de får samma {@code ProblemDetail}-format som övriga fel.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

	/**
	 * Anropen som görs utan att vara inloggad. De kräver ingen CSRF-token, och inloggningscookien läses
	 * inte, så att en utgången cookie inte stoppar dem.
	 */
	static final RequestMatcher WITHOUT_LOGIN = new OrRequestMatcher(
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/login"),
			// Skyddas av engångskoden i stället för inloggning.
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/set-password"));

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
			UserAccountRepository accounts) throws Exception {
		ProblemDetailSecurityHandler problemHandler = new ProblemDetailSecurityHandler();
		http
			// Inloggningen ligger i en cookie, så anrop som ändrar data kräver en CSRF-token. Klienten läser
			// den ur XSRF-TOKEN-cookien och skickar den i X-XSRF-TOKEN.
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository()).ignoringRequestMatchers(WITHOUT_LOGIN))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilter(cookieAuthenticationFilter(jwtDecoder, accounts, problemHandler))
			.authorizeHttpRequests(auth -> auth
				// Felsidan svarar med ProblemDetail, se ProblemDetailErrorController.
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers("/api/v1/public/**").permitAll()
				.requestMatchers(WITHOUT_LOGIN).permitAll()
				.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/api/v1/**").authenticated()
				.anyRequest().denyAll())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(problemHandler)
				.accessDeniedHandler(problemHandler));
		return http.build();
	}

	/**
	 * Läser JWT:n ur inloggningscookien och verifierar den. Filtret kopplas in för hand i stället för med
	 * {@code oauth2ResourceServer()}, eftersom den undantar varje anrop som bär en token från CSRF-skyddet.
	 * Det är rätt för en token i {@code Authorization}-huvudet, men inte för en cookie som webbläsaren
	 * skickar med av sig själv.
	 */
	private static BearerTokenAuthenticationFilter cookieAuthenticationFilter(JwtDecoder jwtDecoder,
			UserAccountRepository accounts, ProblemDetailSecurityHandler problemHandler) {
		JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
		provider.setJwtAuthenticationConverter(new AccountJwtAuthenticationConverter(accounts));
		BearerTokenAuthenticationConverter converter = new BearerTokenAuthenticationConverter();
		converter.setBearerTokenResolver(new CookieBearerTokenResolver());
		BearerTokenAuthenticationFilter filter = new BearerTokenAuthenticationFilter(new ProviderManager(provider),
				converter);
		filter.setAuthenticationEntryPoint(problemHandler);
		filter.setAuthenticationFailureHandler(new AuthenticationEntryPointFailureHandler(problemHandler));
		return filter;
	}

	/** Läsbar för klientens JavaScript, men bara över HTTPS och bara från samma webbplats. */
	private static CookieCsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Strict").path("/"));
		return repository;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
