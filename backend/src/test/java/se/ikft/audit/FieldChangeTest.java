package se.ikft.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FieldChangeTest {

	@Test
	void ettFaltUtanNamnAvvisas() {
		assertThatThrownBy(() -> FieldChange.named(" ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> FieldChange.named(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void bokstavligaVardenSparasAvskurnaTillKolumnensGrans() {
		String langt = "a".repeat(FieldChange.MAX_VALUE_LENGTH + 50);

		FieldChange andring = FieldChange.withValues("status", langt, langt);

		assertThat(andring.before()).hasSize(FieldChange.MAX_VALUE_LENGTH);
		assertThat(andring.after()).hasSize(FieldChange.MAX_VALUE_LENGTH);
	}

	@Test
	void namedHarIngaVarden() {
		FieldChange andring = FieldChange.named("firstName");

		assertThat(andring.field()).isEqualTo("firstName");
		assertThat(andring.before()).isNull();
		assertThat(andring.after()).isNull();
	}

}
