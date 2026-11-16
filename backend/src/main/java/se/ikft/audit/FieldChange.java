package se.ikft.audit;

/**
 * Ett fält som ändrades. Värdena sparas bara när de behövs för att kunna visa hur en siffra i ett
 * bidragsunderlag uppstod. För övriga fält, som namn och adress, används {@link #named(String)}, som bara
 * visar att fältet ändrats.
 *
 * @param field fältets namn
 * @param before värdet före ändringen, eller null om värdet inte sparas eller saknades
 * @param after värdet efter ändringen, eller null om värdet inte sparas eller togs bort
 */
public record FieldChange(String field, String before, String after) {

	/** Gränsen i kolumnerna {@code audit_log_change.before_value} och {@code after_value}. */
	static final int MAX_VALUE_LENGTH = 255;

	public FieldChange {
		if (field == null || field.isBlank()) {
			throw new IllegalArgumentException("Fältet måste ha ett namn.");
		}
		before = cut(before);
		after = cut(after);
	}

	/** Loggar bara att fältet ändrats, utan värden. */
	public static FieldChange named(String field) {
		return new FieldChange(field, null, null);
	}

	public static FieldChange withValues(String field, String before, String after) {
		return new FieldChange(field, before, after);
	}

	private static String cut(String value) {
		return value != null && value.length() > MAX_VALUE_LENGTH ? value.substring(0, MAX_VALUE_LENGTH) : value;
	}

}
