package se.ikft.assignment;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface DocumenterAssignmentRepository extends JpaRepository<DocumenterAssignment, UUID> {

	/**
	 * Uppdrag som inte har tagit slut före det givna datumet. Om de gäller avgörs av
	 * {@link DocumenterAssignments}.
	 */
	List<DocumenterAssignment> findByEndDateGreaterThanEqual(LocalDate date);

	/** Medlemmens för- och efternamn. */
	@Query(value = "SELECT first_name || ' ' || last_name FROM member WHERE id = :memberId", nativeQuery = true)
	Optional<String> findMemberName(UUID memberId);

}
