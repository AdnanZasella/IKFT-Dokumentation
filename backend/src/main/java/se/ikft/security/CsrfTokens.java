package se.ikft.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

/**
 * Byter CSRF-token vid inloggning, som Spring Security annars gör när någon loggar in. Inloggningen går
 * genom en vanlig controller och inte genom säkerhetskedjan, så det görs här. En token som någon har sett
 * före inloggningen, t.ex. i en delad webbläsare, gäller då inte efter den.
 */
@Component
@ConditionalOnWebApplication
public class CsrfTokens {

	private final CsrfTokenRepository repository;

	CsrfTokens(CsrfTokenRepository repository) {
		this.repository = repository;
	}

	/** Sätter en ny CSRF-cookie i svaret. Den gamla token slutar gälla. */
	public void renew(HttpServletRequest request, HttpServletResponse response) {
		repository.saveToken(repository.generateToken(request), request, response);
	}

}
