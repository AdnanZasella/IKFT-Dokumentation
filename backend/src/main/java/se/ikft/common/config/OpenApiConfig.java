package se.ikft.common.config;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rubrik och regler för OpenAPI-beskrivningen. Beskrivningen är avstängd som standard (se
 * {@code springdoc.api-docs.enabled}) och används för att generera typerna till admin-appen.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	@Bean
	OpenAPI ikftOpenApi() {
		return new OpenAPI().info(new Info().title("IKFT API").version("v1"));
	}

	/**
	 * Alla fält i ett svar finns med i JSON-svaret utom de som är nullbara ({@code @Schema(nullable = true)}).
	 * Utan det här blir varje fält valfritt i TypeScript-typerna. Förfrågningar (klasser som slutar på
	 * {@code Request}) styrs av valideringsreglerna och rörs inte.
	 */
	@Bean
	OpenApiCustomizer svarsfaltArObligatoriska() {
		return openApi -> {
			if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
				return;
			}
			openApi.getComponents().getSchemas().forEach((namn, schema) -> {
				if (!namn.endsWith("Request")) {
					goraObligatoriska(schema);
				}
			});
		};
	}

	@SuppressWarnings("rawtypes")
	private static void goraObligatoriska(Schema<?> schema) {
		if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
			List<String> obligatoriska = new ArrayList<>();
			schema.getProperties().forEach((namn, egenskap) -> {
				if (!arNullbar((Schema<?>) egenskap)) {
					obligatoriska.add(namn);
				}
			});
			schema.setRequired(obligatoriska.isEmpty() ? null : obligatoriska);
		}
		if (schema.getAllOf() != null) {
			schema.getAllOf().forEach(del -> goraObligatoriska((Schema<?>) del));
		}
	}

	private static boolean arNullbar(Schema<?> egenskap) {
		return Boolean.TRUE.equals(egenskap.getNullable())
				|| (egenskap.getTypes() != null && egenskap.getTypes().contains("null"));
	}

}
