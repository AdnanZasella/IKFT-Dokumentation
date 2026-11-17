package se.ikft.auth;

import se.ikft.account.Role;

/**
 * Det inloggade kontot, som det visas för klienten efter inloggningen och i {@code GET /auth/me}.
 *
 * @param role kontots roll
 * @param holderName innehavarens namn, eller {@code null} om kontot saknar innehavare
 * @param mustChangePassword om lösenordet måste bytas innan något annat är tillåtet, det vill säga efter ett
 *            innehavarbyte
 */
record AccountInfo(Role role, String holderName, boolean mustChangePassword) {
}
