package se.ikft.common.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

	@ExceptionHandler(DataIntegrityViolationException.class)
	ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
		// T.ex. en dubblett av ett unikt värde. Databasens meddelande skickas inte till klienten.
		log.info("Databasen avvisade ändringen: {}", ex.getMostSpecificCause().getMessage());
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
