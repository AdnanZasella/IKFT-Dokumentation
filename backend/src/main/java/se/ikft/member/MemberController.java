package se.ikft.member;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import se.ikft.member.MemberResponses.Details;
import se.ikft.member.MemberResponses.FifsFeeInfo;
import se.ikft.member.MemberResponses.ListItem;
import se.ikft.member.MemberResponses.RoleInfo;
import se.ikft.security.CurrentAccount;

/**
 * Medlemsregistret under {@code /api/v1/members}. Allt utom listan kräver Admin. Spärren ligger här i
 * backend och inte bara i gränssnittet.
 */
@RestController
@ConditionalOnWebApplication
@RequestMapping("/api/v1/members")
class MemberController {

	private final MemberService service;

	MemberController(MemberService service) {
		this.service = service;
	}

	/** Admin och Dokumenterare. Dokumenteraren ser bara namn och åldersgrupp för aktiva medlemmar. */
	@GetMapping
	@PreAuthorize("hasAnyRole('ADMIN', 'DOCUMENTER')")
	List<ListItem> list(@RequestParam(required = false) MemberStatus status, Authentication authentication) {
		return service.list(CurrentAccount.role(authentication), status);
	}

	@PostMapping
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	Details create(@Valid @RequestBody MemberRequest request) {
		return service.create(request);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	Details get(@PathVariable UUID id) {
		return service.get(id);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	Details update(@PathVariable UUID id, @Valid @RequestBody MemberRequest request) {
		return service.update(id, request);
	}

	/** Inaktiverar. Medlemmar raderas aldrig. */
	@DeleteMapping("/{id}")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void deactivate(@PathVariable UUID id) {
		service.deactivate(id);
	}

	@PostMapping("/{id}/roles")
	@PreAuthorize("hasRole('ADMIN')")
	@ResponseStatus(HttpStatus.CREATED)
	RoleInfo addRole(@PathVariable UUID id, @Valid @RequestBody MemberRoleRequest request) {
		return service.addRole(id, request);
	}

	/** Markerar att medlemmen betalat moskéavgiften via FIFS ett år. Föreningens egen medlemsavgift hanteras senare. */
	@PutMapping("/{id}/fifs-fees/{year}")
	@PreAuthorize("hasRole('ADMIN')")
	FifsFeeInfo markFifsFeePaid(@PathVariable UUID id, @PathVariable int year) {
		return service.markFifsFeePaid(id, year);
	}

}
