package se.ikft.auth;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface SetupCodeRepository extends JpaRepository<SetupCode, UUID> {

	/** Kontots koder som varken är använda eller har gått ut. */
	@Query("""
			select c from SetupCode c
			where c.userAccountId = :accountId and c.usedAt is null and c.validUntil > :now
			""")
	List<SetupCode> findValid(UUID accountId, Instant now);

}
