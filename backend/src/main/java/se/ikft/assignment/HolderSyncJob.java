package se.ikft.assignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import se.ikft.common.config.TimeConfig;

/**
 * Ser till att Dokumenterare-kontots innehavare är den som är aktiv dokumenterare, vid midnatt
 * (Europe/Stockholm) och när appen startar. Uppstarten fångar det som missades om appen låg nere vid
 * midnatt. Jobbet byter innehavare när ett tillfälligt uppdrag löpt ut, när ett uppdrag som lagts i förväg
 * börjar gälla, och när ett årsskifte passerats utan nytt uppdrag (då får kontot ingen innehavare). Själva
 * avvägningen görs av {@link DocumenterAppointmentService#syncHolder()}.
 * <p>
 * Ett fel ska inte stoppa appen eller schemaläggaren: det loggas på ERROR, och nästa körning försöker igen.
 * <p>
 * Tidpunkten kan stängas av med {@code ikft.assignment.sync-cron=-}, vilket testerna gör så att jobbet inte
 * startar mitt i ett test. Uppstartskörningen påverkas inte.
 */
@Component
class HolderSyncJob implements ApplicationRunner {

	/** Sekund, minut, timme, dag, månad, veckodag: 00:00:00 varje dag. */
	static final String DEFAULT_CRON = "0 0 0 * * *";

	/** Midnatt varje dag. Inställningen finns bara för att kunna stängas av i tester. */
	static final String CRON = "${ikft.assignment.sync-cron:" + DEFAULT_CRON + "}";

	private static final Logger log = LoggerFactory.getLogger(HolderSyncJob.class);

	private final DocumenterAppointmentService appointments;

	HolderSyncJob(DocumenterAppointmentService appointments) {
		this.appointments = appointments;
	}

	@Scheduled(cron = CRON, zone = TimeConfig.ZONE_ID)
	void atMidnight() {
		sync("vid midnatt");
	}

	@Override
	public void run(ApplicationArguments args) {
		sync("vid uppstart");
	}

	/** Kan anropas direkt, till exempel av tester. Kastar aldrig. */
	void sync(String when) {
		try {
			boolean changed = appointments.syncHolder();
			log.info("Innehavarkontrollen {} är klar. {}", when,
					changed ? "Innehavaren ändrades." : "Innehavaren var redan rätt.");
		}
		catch (RuntimeException ex) {
			log.error("Innehavarkontrollen {} misslyckades. Nästa körning försöker igen.", when, ex);
		}
	}

}
