package se.ikft.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spärrar allt utom att byta lösenord för ett konto som måste byta det, det vill säga när innehavaren har bytts.
 * Inloggning, utloggning, förnyelse och {@code set-password} är öppna och berörs inte. Filtret sitter efter
 * inloggningsfiltret, så det ser kontot som {@link AccountJwtAuthenticationConverter} läst från databasen, och
 * spärren släpper så fort lösenordet har bytts.
 */
class PasswordChangeRequiredFilter extends OncePerRequestFilter {

	static final String DETAIL = "Lösenordet måste bytas innan du kan fortsätta.";

	/** Det som är tillåtet med flaggan satt: att se vem som är inloggad och att byta lösenord. */
	private static final RequestMatcher ALLOWED = new OrRequestMatcher(
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/auth/me"),
			PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/auth/change-password"));

	private final ProblemDetailSecurityHandler problemHandler;

	PasswordChangeRequiredFilter(ProblemDetailSecurityHandler problemHandler) {
		this.problemHandler = problemHandler;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.isAuthenticated()
				&& CurrentAccount.mustChangePassword(authentication) && !ALLOWED.matches(request)) {
			problemHandler.passwordChangeRequired(response);
			return;
		}
		chain.doFilter(request, response);
	}

}
