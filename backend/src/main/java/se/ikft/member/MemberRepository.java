package se.ikft.member;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, UUID> {

	List<Member> findAllByOrderByLastNameAscFirstNameAsc();

	List<Member> findAllByStatusOrderByLastNameAscFirstNameAsc(MemberStatus status);

}
