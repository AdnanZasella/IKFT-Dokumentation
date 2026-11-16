package se.ikft.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import se.ikft.auth.LoginService.LoginResult;
import se.ikft.security.AccessTokens;
import se.ikft.security.CsrfTokens;
import se.ikft.security.RefreshTokens;
import se.ikft.security.CurrentAccount;

@RestController
@ConditionalOnWebApplication
@RequestMapping("/api/v1/auth")
class AuthController {

	private final SetupCodeService setupCodes;

	private final LoginService loginService;

	private final AccountInfoService accountInfo;

	private final CsrfTokens csrfTokens;

	private final AccessTokens accessTokens;

	private final RefreshTokens refreshTokens;

	AuthController(SetupCodeService setupCodes, LoginService loginService, AccountInfoService accountInfo,
			CsrfTokens csrfTokens, AccessTokens accessTokens, RefreshTokens refreshTokens) {
		this.setupCodes = setupCodes;
		this.loginService = loginService;
		this.accountInfo = accountInfo;
		this.csrfTokens = csrfTokens;
		this.accessTokens = accessTokens;
		this.refreshTokens = refreshTokens;
	}

	/** Det inloggade kontot. */
	@GetMapping("/me")
	AccountInfo me(Authentication authentication) {
		return accountInfo.get(CurrentAccount.id(authentication), CurrentAccount.role(authentication));
	}

	/**
	 * Sätter inloggningscookien och en ny CSRF-cookie, och svarar med kontot, så att klienten inte behöver
	 * fråga efter det.
	 */
	@PostMapping("/login")
	AccountInfo login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest,
			HttpServletResponse response) {
		LoginResult result = loginService.login(request.username(), request.password());
		// addHeader och inte ResponseEntity.header, som skriver över den nya CSRF-cookien i stället för att
		// lägga till en cookie.
		response.addHeader(HttpHeaders.SET_COOKIE, result.cookie().toString());
		response.addHeader(HttpHeaders.SET_COOKIE, result.refreshCookie().toString());
		csrfTokens.renew(httpRequest, response);
		return result.account();
	}

	/**
	 * Ger en ny åtkomsttoken mot förnyelsecookien. Öppen, eftersom åtkomsttoken då normalt har gått ut,
	 * men kräver CSRF-token och en giltig förnyelsetoken.
	 */
	@PostMapping("/refresh")
	AccountInfo refresh(@CookieValue(name = RefreshTokens.COOKIE_NAME, required = false) String refreshToken,
			HttpServletResponse response) {
		LoginResult result = loginService.refresh(refreshToken);
		response.addHeader(HttpHeaders.SET_COOKIE, result.cookie().toString());
		return result.account();
	}

	/**
	 * Rensar båda cookies. Öppen, så att den som har en utgången åtkomsttoken också kan logga ut. Tokens
	 * ogiltigförklaras inte på servern, det görs genom att räkna upp tokenversionen.
	 */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(HttpServletResponse response) {
		response.addHeader(HttpHeaders.SET_COOKIE, accessTokens.clear().toString());
		response.addHeader(HttpHeaders.SET_COOKIE, refreshTokens.clear().toString());
	}

	/** Öppen, eftersom den som sätter lösenordet inte kan vara inloggad. Kräver en giltig engångskod. */
	@PostMapping("/set-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void setPassword(@Valid @RequestBody SetPasswordRequest request) {
		setupCodes.setPassword(request.username(), request.code(), request.newPassword());
	}

}
