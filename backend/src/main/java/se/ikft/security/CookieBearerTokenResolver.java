package se.ikft.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * Läser token från inloggningscookien. {@code Authorization}-huvudet används inte, eftersom token
 * aldrig ska finnas där JavaScript kan läsa den.
 */
class CookieBearerTokenResolver implements BearerTokenResolver {

	@Override
	public String resolve(HttpServletRequest request) {
		if (request.getCookies() == null || SecurityConfig.WITHOUT_LOGIN.matches(request)) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (AccessTokens.COOKIE_NAME.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
				return cookie.getValue();
			}
		}
		return null;
	}

}
