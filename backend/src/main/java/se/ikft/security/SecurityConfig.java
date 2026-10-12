package se.ikft.security;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
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
 * <p>
 * Kedjorna skapas bara när appen körs som webbapp. Som engångskommando (återställningen av
 * Admin-lösenordet) finns ingen inloggning, och då behövs ingen JWT-nyckel.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

	/**
	 * Med prefixet {@code __Host-} kan cookien bara sättas av värden själv, inte av en annan subdomän.
	 * Klienten skickar token i {@code X-XSRF-TOKEN}.
	 */
	public static final String CSRF_COOKIE_NAME = "__Host-XSRF-TOKEN";

	/**
	 * Det som är öppet för alla. Det har en egen kedja utan inloggning och utan CSRF, så att en ogiltig
	 * inloggningscookie inte ger 401 och svaren inte får någon cookie som hindrar cachning.
	 */
	static final String[] PUBLIC_PATHS = { "/api/v1/public/**", "/actuator/health", "/actuator/health/**" };

	/**
	 * Anropen i admin-appen som görs utan att vara inloggad. Inloggningscookien läses inte, så att en
	 * utgången cookie inte stoppar dem. De kräver CSRF-token som alla andra anrop som ändrar data, så att
	 * en annan webbplats inte kan logga in besökaren på ett annat konto. Klienten får CSRF-cookien i
	 * svaret på sitt första anrop, till exempel {@code GET /api/v1/auth/me}.
	 */
	static final RequestMatcher WITHOUT_LOGIN = new OrRequestMatcher(
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/login"),
			// Skyddas av engångskoden i stället för inloggning.
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/set-password"));

	@Bean
	@Order(1)
	@ConditionalOnWebApplication
	SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
		ProblemDetailSecurityHandler problemHandler = new ProblemDetailSecurityHandler();
		http.securityMatcher(PUBLIC_PATHS)
			.csrf(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(problemHandler)
				.accessDeniedHandler(problemHandler));
		return http.build();
	}

	@Bean
	@Order(2)
	@ConditionalOnWebApplication
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
			UserAccountRepository accounts) throws Exception {
		ProblemDetailSecurityHandler problemHandler = new ProblemDetailSecurityHandler();
		http
			// Inloggningen ligger i en cookie, så anrop som ändrar data kräver en CSRF-token. Klienten läser
			// den ur CSRF-cookien och skickar den i X-XSRF-TOKEN.
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository()))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.addFilter(cookieAuthenticationFilter(jwtDecoder, accounts, problemHandler))
			.authorizeHttpRequests(auth -> auth
				// Felsidan svarar med ProblemDetail, se ProblemDetailErrorController.
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(WITHOUT_LOGIN).permitAll()
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
		repository.setCookieName(CSRF_COOKIE_NAME);
		repository.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Strict").path("/"));
		return repository;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
