package se.ikft.security;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.Cookie;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * CSRF-cookie och samma token i {@code X-XSRF-TOKEN}, som klienten skickar. Används i stället för
 * {@code SecurityMockMvcRequestPostProcessors.csrf()}, som byter ut CSRF-filtrets lagring i den delade
 * testkontexten och därmed påverkar andra testklasser.
 */
public final class TestCsrf {

	private static final String TOKEN = "test-csrf-token";

	private TestCsrf() {
	}

	public static RequestPostProcessor csrfToken() {
		return request -> {
			List<Cookie> cookies = new ArrayList<>();
			if (request.getCookies() != null) {
				cookies.addAll(List.of(request.getCookies()));
			}
			cookies.add(new Cookie(SecurityConfig.CSRF_COOKIE_NAME, TOKEN));
			request.setCookies(cookies.toArray(Cookie[]::new));
			request.addHeader("X-XSRF-TOKEN", TOKEN);
			return request;
		};
	}

}
