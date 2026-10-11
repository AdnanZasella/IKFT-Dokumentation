package se.ikft.auth;

import java.time.LocalDateTime;
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
 * Ger en väg in till Admin-kontot utan att något lösenord finns i repot. Saknar kontot både lösenord
 * och giltig kod skapas en kod som skrivs ut i loggen. Har koden tappats bort skrivs en ny ut vid
 * nästa start efter att raden i {@code setup_code} raderats eller koden gått ut.
 * <p>
 * Har Admin glömt lösenordet, eller har någon annan satt det, startar den som driftar servern appen
 * med {@code ikft.auth.reset-admin-password=true}. Då skapas en ny kod även om kontot har lösenord.
 * Det kräver åtkomst till servern, som är det enda som går att lita på när Admin inte kan logga in.
 */
@Component
class AdminSetupCodeInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSetupCodeInitializer.class);

	private final UserAccountRepository accounts;

	private final SetupCodeService setupCodes;

	private final AuthProperties properties;

	AdminSetupCodeInitializer(UserAccountRepository accounts, SetupCodeService setupCodes,
			AuthProperties properties) {
		this.accounts = accounts;
		this.setupCodes = setupCodes;
		this.properties = properties;
	}

	@Override
	public void run(ApplicationArguments args) {
		UserAccount admin = accounts.findByRole(Role.ADMIN)
			.orElseThrow(() -> new IllegalStateException("Admin-kontot saknas i databasen."));
		if (properties.resetAdminPassword()) {
			NewSetupCode code = setupCodes.createFor(admin);
			log.warn("Admin-lösenordet återställs (ikft.auth.reset-admin-password). Användarnamn: {}, "
					+ "engångskod: {} (gäller till {}). Sätt lösenordet med POST /api/v1/auth/set-password "
					+ "och ta sedan bort inställningen, annars skapas en ny kod vid varje start.",
					admin.getUsername(), code.code(), localValidUntil(code));
			return;
		}
		Optional<NewSetupCode> created = setupCodes.createIfMissing(admin);
		if (created.isEmpty()) {
			return;
		}
		NewSetupCode code = created.get();
		// Det enda stället där koden finns i klartext. Den skrivs bara ut när den skapas.
		log.warn("Admin-kontot saknar lösenord. Användarnamn: {}, engångskod: {} (gäller till {}). "
				+ "Sätt lösenordet med POST /api/v1/auth/set-password.", admin.getUsername(), code.code(),
				localValidUntil(code));
	}

	private static LocalDateTime localValidUntil(NewSetupCode code) {
		return code.validUntil().atZone(TimeConfig.ZONE).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES);
	}

}
