package se.ikft.member;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface MemberFifsFeeRepository extends JpaRepository<MemberFifsFee, UUID> {

	Optional<MemberFifsFee> findByMemberIdAndYear(UUID memberId, int year);

	List<MemberFifsFee> findAllByMemberIdOrderByYear(UUID memberId);

}
