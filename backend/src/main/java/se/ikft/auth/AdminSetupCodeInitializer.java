package se.ikft.auth;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
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
 * Har Admin glömt lösenordet, eller har någon annan satt det, kör den som driftar servern appen som
 * ett engångskommando med {@code ikft.auth.reset-admin-password=true}. Det kräver åtkomst till
 * servern, som är det enda som går att lita på när Admin inte kan logga in. Lösenordet tas bort, gamla
 * sessioner slutar gälla, en ny kod skrivs ut och appen stängs med kod 0. Inställningen får inte ligga
 * i tjänstens konfiguration: en tjänst som startas om automatiskt skulle återställa lösenordet och
 * ersätta koden vid varje omstart.
 */
@Component
class AdminSetupCodeInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminSetupCodeInitializer.class);

	private final UserAccountRepository accounts;

	private final SetupCodeService setupCodes;

	private final AuthProperties properties;

	private final ConfigurableApplicationContext context;

	AdminSetupCodeInitializer(UserAccountRepository accounts, SetupCodeService setupCodes,
			AuthProperties properties, ConfigurableApplicationContext context) {
		this.accounts = accounts;
		this.setupCodes = setupCodes;
		this.properties = properties;
		this.context = context;
	}

	@Override
	public void run(ApplicationArguments args) {
		UserAccount admin = accounts.findByRole(Role.ADMIN)
			.orElseThrow(() -> new IllegalStateException("Admin-kontot saknas i databasen."));
		if (properties.resetAdminPassword()) {
			NewSetupCode code = setupCodes.reset(admin);
			logCode("Admin-lösenordet är återställt (ikft.auth.reset-admin-password). Det gamla lösenordet "
					+ "och alla sessioner gäller inte längre.", admin, code,
					"Appen stängs nu. Ta bort inställningen, starta appen som vanligt och sätt lösenordet med "
							+ "POST /api/v1/auth/set-password.");
			exit();
			return;
		}
		// Det enda stället där koden finns i klartext. Den skrivs bara ut när den skapas.
		setupCodes.createIfMissing(admin)
			.ifPresent(code -> logCode("Admin-kontot saknar lösenord.", admin, code,
					"Sätt lösenordet med POST /api/v1/auth/set-password."));
	}

	/** Stänger appen efter en återställning. Ersätts i tester. */
	void exit() {
		System.exit(SpringApplication.exit(context, () -> 0));
	}

	private static void logCode(String before, UserAccount admin, NewSetupCode code, String after) {
		LocalDateTime validUntil = code.validUntil()
			.atZone(TimeConfig.ZONE)
			.toLocalDateTime()
			.truncatedTo(ChronoUnit.MINUTES);
		log.warn("{} Användarnamn: {}, engångskod: {} (gäller till {}). {}", before, admin.getUsername(),
				code.code(), validUntil, after);
	}

}
