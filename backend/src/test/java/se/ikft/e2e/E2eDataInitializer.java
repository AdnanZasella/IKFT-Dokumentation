package se.ikft.e2e;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Gör en tom databas redo för E2E-testerna i {@code e2e/}: kända lösenord på de två kontona och ett par påhittade
 * medlemmar. Klassen ligger i {@code src/test} och aktiveras bara med profilen {@code e2e}, så den följer inte med
 * den byggda appen och körs aldrig i drift. Starta backenden med
 * {@code ./mvnw spring-boot:test-run -Dspring-boot.run.profiles=e2e}, se docs/07-teststrategi.md.
 * <p>
 * Lösenorden är publika och gäller bara databasen som Testcontainers startar för E2E-körningen. De finns också i
 * {@code e2e/support/konton.ts}. Lösenordet som {@code AdminSetupCodeInitializer} skriver ut i loggen används
 * aldrig i E2E, eftersom det vore skört att läsa loggar.
 * <p>
 * Körs före {@code AdminSetupCodeInitializer}, så att Admin redan har ett lösenord och ingen engångskod skapas.
 */
@Component
@Profile("e2e")
@Order(Ordered.HIGHEST_PRECEDENCE)
class E2eDataInitializer implements ApplicationRunner {

	static final String ADMIN_PASSWORD = "e2e-admin-losenord-1";

	static final String DOCUMENTER_PASSWORD = "e2e-dokumenterare-losenord-1";

	/** Påhittade personer. Samma namn finns inte i någon annan testdata, så att de syns om de hamnar fel. */
	static final String[][] MEMBERS = { { "Berit", "Exempelsson", "1985-03-12" },
			{ "Cemal", "Provsson", "1990-09-30" }, { "Dina", "Testdotter", "1978-11-02" } };

	private final JdbcClient jdbc;

	private final PasswordEncoder passwordEncoder;

	E2eDataInitializer(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		setPassword("admin", ADMIN_PASSWORD);
		setPassword("dokumenterare", DOCUMENTER_PASSWORD);
		// Bara i en tom databas, så att ett nytt anrop inte ger dubbletter.
		if (jdbc.sql("SELECT count(*) FROM member").query(Long.class).single() == 0) {
			for (String[] member : MEMBERS) {
				jdbc.sql("""
						INSERT INTO member (first_name, last_name, birth_date, home_municipality, member_since)
						VALUES (:first, :last, CAST(:birth AS date), 'Trollhättan', CAST('2020-01-01' AS date))
						""").param("first", member[0]).param("last", member[1]).param("birth", member[2]).update();
			}
		}
	}

	/** Räknar upp tokenversionen, så att inloggningar från före anropet slutar gälla. */
	private void setPassword(String username, String password) {
		jdbc.sql("""
				UPDATE user_account
				SET password_hash = :hash, must_change_password = false, holder_member_id = NULL,
				    token_version = token_version + 1
				WHERE username = :username
				""").param("hash", passwordEncoder.encode(password)).param("username", username).update();
	}

}
