package se.ikft.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;

import se.ikft.TestcontainersConfiguration;
import se.ikft.security.AccessTokens;
import se.ikft.security.SecurityConfig;

/**
 * Cookies från inloggningen genom en riktig Tomcat. MockMvc skiljer sig från servern här: den behåller
 * cookies som servern skulle skriva över, och visar inte {@code SameSite} för cookies som lagts till
 * som {@code Cookie}-objekt.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class LoginCookiesServerTest {

	private static final String LOSENORD = "ett-langt-losenord";

	@LocalServerPort
	int port;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@BeforeEach
	void forbered() {
		aterstall();
		jdbc.sql("UPDATE user_account SET password_hash = ? WHERE username = 'admin'")
			.param(passwordEncoder.encode(LOSENORD))
			.update();
	}

	@AfterEach
	void aterstall() {
		jdbc.sql("UPDATE user_account SET password_hash = NULL, token_version = 0").update();
	}

	@Test
	void forstaAnropetSatterCsrfCookieOchInloggningenSatterInloggningscookie() throws Exception {
		HttpClient client = HttpClient.newHttpClient();
		// Klientens första anrop, innan den är inloggad. Svaret är 401 men sätter CSRF-cookien.
		HttpResponse<String> forsta = client.send(HttpRequest.newBuilder(url("/api/v1/auth/me")).build(),
				HttpResponse.BodyHandlers.ofString());

		assertThat(forsta.statusCode()).isEqualTo(401);
		List<String> csrf = cookie(forsta.headers().allValues("Set-Cookie"), SecurityConfig.CSRF_COOKIE_NAME);
		assertThat(csrf).contains("Secure", "SameSite=Strict", "Path=/").doesNotContain("HttpOnly");

		String csrfCookie = csrf.getFirst();
		HttpRequest login = HttpRequest.newBuilder(url("/api/v1/auth/login"))
			.header("Content-Type", "application/json")
			.header("Cookie", csrfCookie)
			.header("X-XSRF-TOKEN", csrfCookie.substring(csrfCookie.indexOf('=') + 1))
			.POST(HttpRequest.BodyPublishers.ofString("""
					{"username": "admin", "password": "%s"}
					""".formatted(LOSENORD)))
			.build();
		HttpResponse<String> response = client.send(login, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(cookie(response.headers().allValues("Set-Cookie"), AccessTokens.COOKIE_NAME)).contains("HttpOnly",
				"Secure", "SameSite=Strict", "Path=/");
	}

	private URI url(String path) {
		return URI.create("http://localhost:" + port + path);
	}

	private static List<String> cookie(List<String> cookies, String name) {
		String cookie = cookies.stream().filter(c -> c.startsWith(name + "=")).findFirst().orElseThrow();
		return List.of(cookie.split(";\\s*"));
	}

}
