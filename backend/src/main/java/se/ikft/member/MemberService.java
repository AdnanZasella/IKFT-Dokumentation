package se.ikft.member;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import se.ikft.account.Role;
import se.ikft.common.config.GrantProperties;
import se.ikft.common.error.BadRequestException;
import se.ikft.common.error.NotFoundException;
import se.ikft.member.MemberResponses.AdminListItem;
import se.ikft.member.MemberResponses.Details;
import se.ikft.member.MemberResponses.FifsFeeInfo;
import se.ikft.member.MemberResponses.ListItem;
import se.ikft.member.MemberResponses.RoleInfo;
import se.ikft.member.MemberResponses.Summary;

/**
 * Medlemsregistret. Behörigheten kontrolleras i {@link MemberController}. Här avgörs vad rollen får se.
 */
@Service
class MemberService {

	/** Äldre födelsedatum än så är nästan säkert ett skrivfel. */
	static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

	static final int EARLIEST_FEE_YEAR = 2000;

	private final MemberRepository members;

	private final MemberFifsFeeRepository fifsFees;

	private final MemberRoleRepository roles;

	private final GrantProperties grant;

	private final Clock clock;

	MemberService(MemberRepository members, MemberFifsFeeRepository fifsFees, MemberRoleRepository roles,
			GrantProperties grant, Clock clock) {
		this.members = members;
		this.fifsFees = fifsFees;
		this.roles = roles;
		this.grant = grant;
		this.clock = clock;
	}

	/**
	 * Admin ser alla medlemmar, eller bara de med {@code status} om den anges. Dokumenteraren ser bara
	 * aktiva medlemmar och bara namn och åldersgrupp, oavsett {@code status}.
	 */
	@Transactional(readOnly = true)
	public List<ListItem> list(Role role, MemberStatus status) {
		LocalDate today = today();
		if (role != Role.ADMIN) {
			return members.findAllByStatusOrderByLastNameAscFirstNameAsc(MemberStatus.ACTIVE)
				.stream()
				.<ListItem>map(m -> new Summary(m.getId(), m.getFirstName(), m.getLastName(), ageGroup(m, today)))
				.toList();
		}
		List<Member> found = status == null ? members.findAllByOrderByLastNameAscFirstNameAsc()
				: members.findAllByStatusOrderByLastNameAscFirstNameAsc(status);
		return found.stream()
			.<ListItem>map(m -> new AdminListItem(m.getId(), m.getFirstName(), m.getLastName(), m.getBirthDate(),
					m.getHomeMunicipality(), m.getStatus(), ageGroup(m, today)))
			.toList();
	}

	@Transactional(readOnly = true)
	public Details get(UUID id) {
		return details(find(id));
	}

	@Transactional
	public Details create(MemberRequest request) {
		Member member = Member.create(checked(request), Instant.now(clock));
		return details(members.save(member));
	}

	@Transactional
	public Details update(UUID id, MemberRequest request) {
		Member member = find(id);
		Instant now = Instant.now(clock);
		member.update(checked(request), now);
		if (request.status() != null) {
			member.setStatus(request.status(), now);
		}
		return details(member);
	}

	/** Inaktiverar medlemmen. Anropas den igen på en inaktiv medlem händer ingenting. */
	@Transactional
	public void deactivate(UUID id) {
		Member member = find(id);
		if (member.getStatus() != MemberStatus.INACTIVE) {
			member.setStatus(MemberStatus.INACTIVE, Instant.now(clock));
		}
	}

	@Transactional
	public RoleInfo addRole(UUID memberId, MemberRoleRequest request) {
		find(memberId);
		String title = request.title().strip();
		if (title.isEmpty()) {
			throw new BadRequestException("Rollens namn får inte vara tomt.");
		}
		if (request.endDate() != null && request.endDate().isBefore(request.startDate())) {
			throw new BadRequestException("Slutdatumet får inte ligga före startdatumet.");
		}
		MemberRole saved = roles
			.save(new MemberRole(memberId, title, request.startDate(), request.endDate(), Instant.now(clock)));
		return roleInfo(saved);
	}

	/**
	 * Markerar moskéavgiften via FIFS som betald för ett år. Är den redan markerad behålls det första datumet.
	 * Ett år bakåt i tiden går bra, eftersom avgiften kan registreras i efterhand.
	 */
	@Transactional
	public FifsFeeInfo markFifsFeePaid(UUID memberId, int year) {
		find(memberId);
		LocalDate today = today();
		if (year < EARLIEST_FEE_YEAR || year > today.getYear() + 1) {
			throw new BadRequestException("Året måste vara mellan " + EARLIEST_FEE_YEAR + " och " + (today.getYear() + 1) + ".");
		}
		MemberFifsFee fee = fifsFees.findByMemberIdAndYear(memberId, year)
			.orElseGet(() -> fifsFees.save(new MemberFifsFee(memberId, year, today, Instant.now(clock))));
		return feeInfo(fee);
	}

	private Member find(UUID id) {
		return members.findById(id).orElseThrow(() -> new NotFoundException("Medlemmen finns inte."));
	}

	/** Rensar och kontrollerar det som annotationerna på {@link MemberRequest} inte kan avgöra. */
	private Member.Details checked(MemberRequest request) {
		LocalDate today = today();
		LocalDate birthDate = request.birthDate();
		if (birthDate.isAfter(today)) {
			throw new BadRequestException("Födelsedatumet får inte ligga i framtiden.");
		}
		if (birthDate.isBefore(EARLIEST_BIRTH_DATE)) {
			throw new BadRequestException("Födelsedatumet är orimligt långt bakåt i tiden.");
		}
		if (request.memberSince() != null && request.memberSince().isBefore(birthDate)) {
			throw new BadRequestException("Medlem sedan får inte ligga före födelsedatumet.");
		}
		return new Member.Details(request.firstName().strip(), request.lastName().strip(), birthDate,
				blankToNull(request.address()), request.homeMunicipality().strip(), request.memberSince(),
				Boolean.TRUE.equals(request.guardianConsent()));
	}

	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.strip();
	}

	private Details details(Member m) {
		List<FifsFeeInfo> feeInfos = fifsFees.findAllByMemberIdOrderByYear(m.getId()).stream().map(MemberService::feeInfo).toList();
		List<RoleInfo> roleInfos = roles.findAllByMemberIdOrderByStartDateDesc(m.getId())
			.stream()
			.map(MemberService::roleInfo)
			.toList();
		return new Details(m.getId(), m.getFirstName(), m.getLastName(), m.getBirthDate(), m.getAddress(),
				m.getHomeMunicipality(), m.getMemberSince(), m.getStatus(), m.isGuardianConsent(), ageGroup(m, today()),
				feeInfos, roleInfos);
	}

	private AgeGroup ageGroup(Member m, LocalDate today) {
		return AgeGroup.of(m.getBirthDate(), today, grant.minAge(), grant.maxAge());
	}

	private static FifsFeeInfo feeInfo(MemberFifsFee fee) {
		return new FifsFeeInfo(fee.getYear(), fee.getPaidOn());
	}

	private static RoleInfo roleInfo(MemberRole role) {
		return new RoleInfo(role.getId(), role.getTitle(), role.getStartDate(), role.getEndDate());
	}

	private LocalDate today() {
		return LocalDate.now(clock);
	}

}
