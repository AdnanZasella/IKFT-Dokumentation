package se.ikft.auth;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;
import se.ikft.auth.SetupCodeService.NewSetupCode;
import se.ikft.common.error.NotFoundException;

/**
 * Engångskod för ett konto under {@code /api/v1/accounts}. Bara Admin kommer åt den.
 */
@RestController
@ConditionalOnWebApplication
@RequestMapping("/api/v1/accounts")
class AccountSetupCodeController {

	private static final Logger log = LoggerFactory.getLogger(AccountSetupCodeController.class);

	private final SetupCodeService setupCodes;

	private final UserAccountRepository accounts;

	AccountSetupCodeController(SetupCodeService setupCodes, UserAccountRepository accounts) {
		this.setupCodes = setupCodes;
		this.accounts = accounts;
	}

	/** Koden i klartext och när den slutar gälla. Klartexten visas bara här och kan inte hämtas igen. */
	record SetupCodeResponse(String code, Instant validUntil) {
	}

	/**
	 * Skapar en ny engångskod för kontot, som reservväg om lösenordet glömts bort. Kontot byter inte
	 * innehavare och behåller sitt lösenord tills koden används. Kontots tidigare koder slutar gälla.
	 */
	@PostMapping("/{id}/setup-code")
	@PreAuthorize("hasRole('ADMIN')")
	SetupCodeResponse setupCode(@PathVariable UUID id) {
		UserAccount account = accounts.findById(id).orElseThrow(() -> new NotFoundException("Kontot finns inte."));
		NewSetupCode created = setupCodes.createFor(account);
		// Koden loggas aldrig. Raden visar att en kod skapats, så att ett övertagande går att spåra.
		log.warn("En engångskod skapades för {}.", account.getUsername());
		return new SetupCodeResponse(created.code(), created.validUntil());
	}

}
