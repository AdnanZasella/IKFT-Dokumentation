package se.ikft.audit;

/**
 * Vilken sorts post en loggpost gäller. Lagras som text i {@code audit_log.entity_type}. Fler läggs till
 * när sammankomster, möten och artiklar byggs.
 */
public enum AuditEntity {

	/** En medlem, inklusive dennes föreningsroller och moskéavgift via FIFS. */
	MEMBER,

	/** Ett användarkonto, för närvarande bara byte av innehavare. */
	ACCOUNT,

	/** Ett dokumenteraruppdrag: när det utses och när det avslutas. */
	DOCUMENTER_ASSIGNMENT

}
