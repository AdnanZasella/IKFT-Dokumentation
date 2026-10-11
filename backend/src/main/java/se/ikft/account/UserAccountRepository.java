package se.ikft.account;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	Optional<UserAccount> findByUsername(String username);

	/** Det finns bara ett konto per roll. */
	Optional<UserAccount> findByRole(Role role);

	/**
	 * Låser kontoraden till transaktionens slut, så att två samtidiga ändringar av samma konto inte
	 * kan krocka. Kräver en pågående transaktion.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<UserAccount> findForUpdateByUsername(String username);

}
