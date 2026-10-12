package se.ikft.account;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	Optional<UserAccount> findByUsername(String username);

	/** Innehavarens för- och efternamn, eller tomt om kontot saknar innehavare. */
	@Query(value = """
			SELECT m.first_name || ' ' || m.last_name FROM user_account a
			JOIN member m ON m.id = a.holder_member_id
			WHERE a.id = :accountId
			""", nativeQuery = true)
	Optional<String> findHolderName(UUID accountId);

	/** Det finns bara ett konto per roll. */
	Optional<UserAccount> findByRole(Role role);

	/**
	 * Låser kontoraden till transaktionens slut, så att två samtidiga ändringar av samma konto inte
	 * kan krocka. Kräver en pågående transaktion.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<UserAccount> findForUpdateByUsername(String username);

	/** Som {@link #findForUpdateByUsername(String)}, men med kontots id. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<UserAccount> findForUpdateById(UUID id);

}
