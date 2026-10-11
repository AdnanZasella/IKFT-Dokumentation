package se.ikft.auth;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import se.ikft.account.Role;
import se.ikft.account.UserAccount;
import se.ikft.account.UserAccountRepository;
import se.ikft.auth.SetupCodeService.NewSetupCode;
import se.ikft.common.config.TimeConfig;

/**
 * Ger en väg in till ett nytt system utan att något lösenord finns i repot: saknar Admin-kontot både
 * lösenord och giltig kod skapas en kod som skrivs ut i loggen. Har koden tappats bort skrivs en ny
 * ut vid nästa start efter att raden i {@code setup_code} raderats eller koden gått ut.
 */
@Component
class AdminSetupCodeInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSetupCodeInitializer.class);

	private final UserAccountRepository accounts;

	private final SetupCodeService setupCodes;

	AdminSetupCodeInitializer(UserAccountRepository accounts, SetupCodeService setupCodes) {
		this.accounts = accounts;
		this.setupCodes = setupCodes;
	}

	@Override
	public void run(ApplicationArguments args) {
		UserAccount admin = accounts.findByRole(Role.ADMIN)
			.orElseThrow(() -> new IllegalStateException("Admin-kontot saknas i databasen."));
		Optional<NewSetupCode> created = setupCodes.createIfMissing(admin);
		if (created.isEmpty()) {
			return;
		}
		NewSetupCode code = created.get();
		// Det enda stället där koden finns i klartext. Den skrivs bara ut när den skapas.
		log.warn("Admin-kontot saknar lösenord. Användarnamn: {}, engångskod: {} (gäller till {}). "
				+ "Sätt lösenordet med POST /api/v1/auth/set-password.", admin.getUsername(), code.code(),
				code.validUntil().atZone(TimeConfig.ZONE).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES));
	}

}
