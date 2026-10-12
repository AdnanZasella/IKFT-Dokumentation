package se.ikft.auth;

import se.ikft.account.Role;

/**
 * Det inloggade kontot, som det visas för klienten efter inloggningen och i {@code GET /auth/me}.
 *
 * @param role kontots roll
 * @param holderName innehavarens namn, eller {@code null} om kontot saknar innehavare
 */
record AccountInfo(Role role, String holderName) {
}
