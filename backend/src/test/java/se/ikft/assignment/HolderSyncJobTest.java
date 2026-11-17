package se.ikft.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

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
		// Sekund, minut, timme, dag, månad, veckodag: 00:00:00 varje dag. Tiden kan bara stängas av, inte ändras.
		assertThat(schemalagt.cron()).isEqualTo("${ikft.assignment.sync-cron:0 0 0 * * *}");
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
