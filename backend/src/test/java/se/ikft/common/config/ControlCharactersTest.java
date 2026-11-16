package se.ikft.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ControlCharactersTest {

	@ParameterizedTest
	@ValueSource(strings = { "\u0000", "a\u0000b", "\u0001", "\u001f", "\u007f", "\u0085", "\u009f", "\u000b",
			"\u000c", "admin\u0000" })
	void kontrolltecknAvvisas(String text) {
		assertThat(ControlCharacters.containsDisallowed(text)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "Trollhättan", "å ä ö é", "två\nrader", "vagn\rretur", "en\ttabb", "😀", "عربي",
			"mellan slag" })
	void vanligTextOchRadbrytningGodtas(String text) {
		assertThat(ControlCharacters.containsDisallowed(text)).isFalse();
	}

	@Test
	void gransvardenRunt31Till32Och126Till127() {
		// 0x1f är det sista kontrolltecknet före mellanslag, 0x7f det första efter de vanliga tecknen.
		assertThat(ControlCharacters.containsDisallowed("\u001f")).isTrue();
		assertThat(ControlCharacters.containsDisallowed(" ")).isFalse();
		assertThat(ControlCharacters.containsDisallowed("~")).isFalse();
		assertThat(ControlCharacters.containsDisallowed("\u007f")).isTrue();
		assertThat(ControlCharacters.containsDisallowed(" ")).isFalse();
	}

}
