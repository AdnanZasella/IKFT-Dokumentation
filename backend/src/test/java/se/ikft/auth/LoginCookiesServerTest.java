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
	void inloggningenSatterBadeInloggningsOchCsrfCookie() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("""
					{"username": "admin", "password": "%s"}
					""".formatted(LOSENORD)))
			.build();

		HttpResponse<String> response = HttpClient.newHttpClient()
			.send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(200);
		List<String> cookies = response.headers().allValues("Set-Cookie");
		assertThat(cookies).hasSize(2);
		assertThat(cookie(cookies, AccessTokens.COOKIE_NAME)).contains("HttpOnly", "Secure", "SameSite=Strict",
				"Path=/");
		assertThat(cookie(cookies, "XSRF-TOKEN")).contains("Secure", "SameSite=Strict", "Path=/")
			.doesNotContain("HttpOnly");
	}

	private static List<String> cookie(List<String> cookies, String name) {
		String cookie = cookies.stream().filter(c -> c.startsWith(name + "=")).findFirst().orElseThrow();
		return List.of(cookie.split(";\\s*"));
	}

}
