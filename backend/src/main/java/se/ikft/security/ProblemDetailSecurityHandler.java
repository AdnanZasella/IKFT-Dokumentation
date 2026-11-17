package se.ikft.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Skriver 401 och 403 från säkerhetskedjan som {@link ProblemDetail}. Svaret skrivs direkt, inte via
 * {@code GlobalExceptionHandler}, så att det alltid får status och innehåll.
 */
class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final HttpMessageConverter<Object> converter = new JacksonJsonHttpMessageConverter();

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws IOException {
		write(response, HttpStatus.UNAUTHORIZED, "Inloggning krävs.");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
			throws IOException {
		write(response, HttpStatus.FORBIDDEN, "Behörighet saknas.");
	}

	/**
	 * 403 för ett konto som måste byta lösenord. Svaret har egenskapen {@code mustChangePassword}, så att
	 * klienten kan skilja det från ett vanligt 403 och skicka användaren till lösenordsbytet.
	 */
	void passwordChangeRequired(HttpServletResponse response) throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
				PasswordChangeRequiredFilter.DETAIL);
		problem.setProperty("mustChangePassword", true);
		write(response, problem);
	}

	private void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		write(response, ProblemDetail.forStatusAndDetail(status, detail));
	}

	private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
		if (response.isCommitted()) {
			return;
		}
		response.setStatus(problem.getStatus());
		converter.write(problem, MediaType.APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
	}

}
