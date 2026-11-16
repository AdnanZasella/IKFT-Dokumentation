package se.ikft.member;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface MemberRoleRepository extends JpaRepository<MemberRole, UUID> {

	List<MemberRole> findAllByMemberIdOrderByStartDateDesc(UUID memberId);

}
