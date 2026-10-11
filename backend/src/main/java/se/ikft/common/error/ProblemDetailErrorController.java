package se.ikft.common.error;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ersätter Boots {@code BasicErrorController}. Fel som uppstår utanför controllers, t.ex. i
 * säkerhetskedjan eller i servern, hamnar på {@code /error} och får då också {@link ProblemDetail}.
 */
@RestController
class ProblemDetailErrorController implements ErrorController {

	@RequestMapping("${server.error.path:${error.path:/error}}")
	ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
		HttpStatus status = (request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code)
				? HttpStatus.resolve(code) : null;
		if (status == null) {
			status = HttpStatus.INTERNAL_SERVER_ERROR;
		}
		ProblemDetail body = ProblemDetail.forStatus(status);
		if (status.is5xxServerError()) {
			body.setDetail("Ett oväntat fel inträffade.");
		}
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
	}

}
