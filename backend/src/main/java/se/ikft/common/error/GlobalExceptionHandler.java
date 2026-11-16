package se.ikft.common.error;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.DisconnectedClientHelper;

/**
 * Översätter undantag från controllers till RFC 9457 {@link ProblemDetail}. 401 och 403 skrivs av
 * säkerhetskedjan och fel utanför controllers av {@link ProblemDetailErrorController}, i samma format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * SQLSTATE-koder som beror på indata: 22021 och 22P05 (ogiltiga tecken, t.ex. NUL) och 22001 (för långt
	 * värde).
	 */
	private static final Set<String> INPUT_SQL_STATES = Set.of("22021", "22P05", "22001");

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail body = ex.getBody();
		body.setDetail("Begäran innehåller ogiltiga fält.");
		List<Map<String, String>> errors = new ArrayList<>();
		ex.getBindingResult()
			.getFieldErrors()
			.forEach(error -> errors.add(error(error.getField(), error.getDefaultMessage())));
		// Fel på hela objektet, t.ex. att ett slutdatum ligger före startdatumet.
		ex.getBindingResult()
			.getGlobalErrors()
			.forEach(error -> errors.add(error(error.getObjectName(), error.getDefaultMessage())));
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/**
	 * Trasig JSON eller text med kontrolltecken, se {@link se.ikft.common.config.JsonConfig}. Jacksons
	 * meddelande skickas inte vidare, eftersom det kan innehålla delar av det som klienten skickade.
	 */
	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Begäran kunde inte läsas. Kontrollera formatet och att texten inte innehåller kontrolltecken.");
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	private static Map<String, String> error(String field, String message) {
		return Map.of("field", field, "message", String.valueOf(message));
	}

	@ExceptionHandler(BadRequestException.class)
	ProblemDetail handleBadRequest(BadRequestException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ProblemDetail handleConflict(ConflictException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	/** Misslyckad inloggning. Övriga säkerhetsundantag hanteras av säkerhetskedjan. */
	@ExceptionHandler(BadCredentialsException.class)
	ProblemDetail handleBadCredentials(BadCredentialsException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
	}

	/** Svaret säger hur länge klienten ska vänta, avrundat uppåt till hela sekunder. */
	@ExceptionHandler(TooManyRequestsException.class)
	ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException ex) {
		long seconds = Math.max(1, (ex.getRetryAfter().toMillis() + 999) / 1000);
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
			.body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) throws Exception {
		String sqlState = (ex.getMostSpecificCause() instanceof SQLException sql) ? sql.getSQLState() : null;
		// Bara de koder som beror på vad klienten skickade är ett fel i begäran. Övriga i klass 22, t.ex.
		// division med noll eller en felaktig typomvandling, är buggar i serverns egen SQL och ska synas.
		if (sqlState != null && sqlState.startsWith("22")) {
			if (INPUT_SQL_STATES.contains(sqlState)) {
				// Databasens meddelande skickas inte till klienten.
				log.info("Databasen avvisade ett värde i begäran ({}): {}", sqlState,
						ex.getMostSpecificCause().getMessage());
				return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Begäran innehåller ogiltiga värden.");
			}
			return handleUnexpected(ex);
		}
		log.info("Databasen avvisade ändringen: {}", ex.getMostSpecificCause().getMessage());
		// T.ex. en dubblett av ett unikt värde.
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Ändringen krockar med befintliga uppgifter.");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail handleUnexpected(Exception ex) throws Exception {
		// Säkerhetsundantag kastas vidare så att säkerhetskedjan kan välja mellan 401 och 403.
		if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
			throw ex;
		}
		// Klienten har kopplat ner, så det finns inget svar att skriva.
		if (DisconnectedClientHelper.isClientDisconnectedException(ex)) {
			return null;
		}
		ResponseStatus responseStatus = AnnotatedElementUtils.findMergedAnnotation(ex.getClass(),
				ResponseStatus.class);
		if (responseStatus != null) {
			ProblemDetail body = ProblemDetail.forStatus(responseStatus.code());
			if (StringUtils.hasText(responseStatus.reason())) {
				body.setDetail(responseStatus.reason());
			}
			return body;
		}
		// Detaljerna loggas men skickas aldrig till klienten.
		log.error("Oväntat fel", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Ett oväntat fel inträffade.");
	}

}
