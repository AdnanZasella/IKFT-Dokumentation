package se.ikft.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import se.ikft.common.config.TimeConfig;

/**
 * Jobbets tidpunkt och att ett fel inte stoppar det. Vad körningen gör testas i
 * {@link DocumenterAppointmentServiceTest} och {@link HolderSyncIntegrationTest}.
 */
class HolderSyncJobTest {

	private final DocumenterAppointmentService appointments = mock(DocumenterAppointmentService.class);

	private final HolderSyncJob job = new HolderSyncJob(appointments);

	@Test
	void jobbetArSchemalagtVidMidnattIStockholm() throws Exception {
		Method metod = HolderSyncJob.class.getDeclaredMethod("atMidnight");

		Scheduled schemalagt = metod.getAnnotation(Scheduled.class);

		assertThat(schemalagt).isNotNull();
		assertThat(schemalagt.zone()).isEqualTo("Europe/Stockholm");
		assertThat(schemalagt.cron()).isEqualTo(HolderSyncJob.CRON).contains(HolderSyncJob.DEFAULT_CRON);
	}

	@Test
	void uttrycketSlarKlockan0000LokalTidAvenPaDagenMedTidsomstallning() {
		CronExpression cron = CronExpression.parse(HolderSyncJob.DEFAULT_CRON);

		// Nyårsafton 23:59:59 (UTC+1) -> nyårsdagen 00:00.
		assertThat(cron.next(ZonedDateTime.of(2026, 12, 31, 23, 59, 59, 0, TimeConfig.ZONE)))
			.isEqualTo(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, TimeConfig.ZONE));
		// Dagen före sommartid (28 mars 2026, klockan flyttas 02:00 -> 03:00): midnatt finns kvar, och nästa är
		// söndagens 00:00 (UTC+1) och inte en timme fel.
		assertThat(cron.next(ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, TimeConfig.ZONE)))
			.isEqualTo(ZonedDateTime.of(2026, 3, 29, 0, 0, 0, 0, TimeConfig.ZONE));
		assertThat(cron.next(ZonedDateTime.of(2026, 3, 29, 12, 0, 0, 0, TimeConfig.ZONE)))
			.isEqualTo(ZonedDateTime.of(2026, 3, 30, 0, 0, 0, 0, TimeConfig.ZONE));
	}

	@Test
	void midnattsjobbetOchUppstartenKallarBadaInnehavarkontrollen() {
		job.atMidnight();
		job.run(null);

		verify(appointments, times(2)).syncHolder();
	}

	@Test
	void ettFelStopparInteJobbetUtanKastasInteVidare() {
		when(appointments.syncHolder()).thenThrow(new IllegalStateException("Dokumenterare-kontot saknas."));

		assertThatCode(job::atMidnight).doesNotThrowAnyException();
		assertThatCode(() -> job.run(null)).doesNotThrowAnyException();
	}

}
