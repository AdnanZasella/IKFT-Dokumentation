package se.ikft.auth;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	private final SetupCodeService setupCodes;

	AuthController(SetupCodeService setupCodes) {
		this.setupCodes = setupCodes;
	}

	/** Öppen, eftersom den som sätter lösenordet inte kan vara inloggad. Kräver en giltig engångskod. */
	@PostMapping("/set-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void setPassword(@Valid @RequestBody SetPasswordRequest request) {
		setupCodes.setPassword(request.username(), request.code(), request.newPassword());
	}

}
