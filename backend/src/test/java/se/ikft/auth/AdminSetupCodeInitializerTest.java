package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import se.ikft.TestcontainersConfiguration;
import se.ikft.account.UserAccountRepository;

/**
 * Koden som skapas vid uppstart när Admin-kontot saknar lösenord. Initieringen körs redan när appen
 * startar, så testerna rensar och anropar den igen.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AdminSetupCodeInitializerTest {

	private static final Pattern LOGGAD_KOD = Pattern.compile("engångskod: ([0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4})");

	@Autowired
	AdminSetupCodeInitializer initializer;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	UserAccountRepository accounts;

	@Autowired
	SetupCodeService setupCodes;

	@BeforeEach
	@AfterEach
	void aterstall() {
		jdbc.sql("DELETE FROM setup_code").update();
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0").update();
	}

	@Test
	void skaparKodOchLoggarDenPaWarnNarAdminSaknarLosenordOchKod(CapturedOutput output) {
		int start = output.getOut().length();
		initializer.run(null);
		String logg = output.getOut().substring(start);

		assertThat(giltigaAdminKoder()).isEqualTo(1);
		Matcher matcher = LOGGAD_KOD.matcher(logg);
		assertThat(matcher.find()).isTrue();
		assertThat(logg).containsPattern("WARN .*Användarnamn: admin, engångskod");
		assertThat(jdbc.sql("SELECT code_hash FROM setup_code").query(String.class).single())
			.isEqualTo(SetupCodes.hash(matcher.group(1)));
	}

	@Test
	void skaparIngenNyKodOmEnGiltigKodRedanFinns(CapturedOutput output) {
		int start = output.getOut().length();
		initializer.run(null);
		initializer.run(null);

		assertThat(giltigaAdminKoder()).isEqualTo(1);
		assertThat(LOGGAD_KOD.matcher(output.getOut().substring(start)).results().count()).isEqualTo(1);
	}

	@Test
	void tvaSamtidigaStarterSkaparBaraEnKod(CapturedOutput output) throws Exception {
		int start = output.getOut().length();
		CountDownLatch klar = new CountDownLatch(1);
		try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
			List<Future<?>> korningar = List.of(pool.submit(() -> {
				klar.await();
				initializer.run(null);
				return null;
			}), pool.submit(() -> {
				klar.await();
				initializer.run(null);
				return null;
			}));
			klar.countDown();
			for (Future<?> korning : korningar) {
				korning.get();
			}
		}

		assertThat(giltigaAdminKoder()).isEqualTo(1);
		assertThat(jdbc.sql("SELECT count(*) FROM setup_code").query(Long.class).single()).isEqualTo(1);
		assertThat(LOGGAD_KOD.matcher(output.getOut().substring(start)).results().count()).isEqualTo(1);
	}

	@Test
	void skaparNyKodNarDenGamlaHarGattUt() {
		initializer.run(null);
		jdbc.sql("UPDATE setup_code SET valid_until = now() - interval '1 minute'").update();

		initializer.run(null);

		assertThat(giltigaAdminKoder()).isEqualTo(1);
	}

	@Test
	void skaparIngenKodOmAdminRedanHarLosenord(CapturedOutput output) {
		jdbc.sql("UPDATE user_account SET password_hash = '$2a$10$finns' WHERE username = 'admin'").update();

		int start = output.getOut().length();
		initializer.run(null);

		assertThat(jdbc.sql("SELECT count(*) FROM setup_code").query(Long.class).single()).isZero();
		assertThat(LOGGAD_KOD.matcher(output.getOut().substring(start)).find()).isFalse();
	}

	@Test
	void aterstallningSkaparKodTrotsLosenordOchKodenByterLosenordet(CapturedOutput output) {
		jdbc.sql("UPDATE user_account SET password_hash = '$2a$10$finns' WHERE username = 'admin'").update();

		int start = output.getOut().length();
		aterstallning().run(null);
		Matcher matcher = LOGGAD_KOD.matcher(output.getOut().substring(start));

		assertThat(matcher.find()).isTrue();
		assertThat(giltigaAdminKoder()).isEqualTo(1);
		setupCodes.setPassword("admin", matcher.group(1), "ett-nytt-adminlosenord");
		assertThat(jdbc.sql("SELECT password_hash FROM user_account WHERE username = 'admin'")
			.query(String.class)
			.single()).isNotEqualTo("$2a$10$finns");
	}

	@Test
	void aterstallningErsatterTidigareKod() {
		initializer.run(null);

		aterstallning().run(null);

		assertThat(giltigaAdminKoder()).isEqualTo(1);
		assertThat(jdbc.sql("SELECT count(*) FROM setup_code").query(Long.class).single()).isEqualTo(2);
	}

	private AdminSetupCodeInitializer aterstallning() {
		return new AdminSetupCodeInitializer(accounts, setupCodes,
				new AuthProperties(Duration.ofHours(24), 12, true));
	}

	private long giltigaAdminKoder() {
		return jdbc.sql("""
				SELECT count(*) FROM setup_code s JOIN user_account a ON a.id = s.user_account_id
				WHERE a.username = 'admin' AND s.used_at IS NULL AND s.valid_until > now()
				""").query(Long.class).single();
	}

}
