package se.ikft.auth;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import se.ikft.auth.LoginService.LoginResult;
import se.ikft.security.CurrentAccount;

@RestController
@ConditionalOnWebApplication
@RequestMapping("/api/v1/auth")
class AuthController {

	private final SetupCodeService setupCodes;

	private final LoginService loginService;

	private final AccountInfoService accountInfo;

	AuthController(SetupCodeService setupCodes, LoginService loginService, AccountInfoService accountInfo) {
		this.setupCodes = setupCodes;
		this.loginService = loginService;
		this.accountInfo = accountInfo;
	}

	/** Det inloggade kontot. */
	@GetMapping("/me")
	AccountInfo me(Authentication authentication) {
		return accountInfo.get(CurrentAccount.id(authentication), CurrentAccount.role(authentication));
	}

	/** Sätter inloggningscookien och svarar med kontot, så att klienten inte behöver fråga efter det. */
	@PostMapping("/login")
	AccountInfo login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
		LoginResult result = loginService.login(request.username(), request.password());
		// addHeader och inte ResponseEntity.header, som skriver över CSRF-cookien från säkerhetskedjan i
		// stället för att lägga till en cookie.
		response.addHeader(HttpHeaders.SET_COOKIE, result.cookie().toString());
		return result.account();
	}

	/** Öppen, eftersom den som sätter lösenordet inte kan vara inloggad. Kräver en giltig engångskod. */
	@PostMapping("/set-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void setPassword(@Valid @RequestBody SetPasswordRequest request) {
		setupCodes.setPassword(request.username(), request.code(), request.newPassword());
	}

}
