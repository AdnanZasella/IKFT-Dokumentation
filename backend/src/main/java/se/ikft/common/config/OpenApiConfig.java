package se.ikft.common.config;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rubrik och regler för OpenAPI-beskrivningen. Beskrivningen är avstängd som standard (se
 * {@code springdoc.api-docs.enabled}) och används för att generera typerna till admin-appen.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

	private static final String PROBLEM_DETAIL = "ProblemDetail";

	@Bean
	OpenAPI ikftOpenApi() {
		return new OpenAPI().info(new Info().title("IKFT API").version("v1"));
	}

	/**
	 * Alla fält i ett svar finns med i JSON-svaret utom de som är nullbara ({@code @Schema(nullable = true)}).
	 * Nullbar betyder här "null eller utelämnad", så TypeScript-typen blir {@code fält?: T | null}. Det gäller också
	 * fält som bara utelämnas ({@code @JsonInclude(NON_NULL)}). Ett nytt fält som kan vara null måste markeras, annars
	 * blir det obligatoriskt. Utan regeln blir varje fält valfritt i TypeScript-typerna. Förfrågningar (klasser som slutar på
	 * {@code Request}) styrs av valideringsreglerna och rörs inte.
	 */
	@Bean
	OpenApiCustomizer svarsfaltArObligatoriska() {
		return openApi -> {
			if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
				return;
			}
			openApi.getComponents().getSchemas().forEach((namn, schema) -> {
				// ProblemDetail har egna, uttryckliga krav (se felsvarArProblemDetail).
				if (!namn.endsWith("Request") && !namn.equals(PROBLEM_DETAIL)) {
					goraObligatoriska(schema);
				}
			});
		};
	}

	/**
	 * Varje felsvar är RFC 9457 {@code ProblemDetail} (se {@code GlobalExceptionHandler}, och 401 och 403 från
	 * säkerhetskedjan), oavsett status. Beskrivningen får därför ett {@code default}-svar med det schemat på varje
	 * endpoint, så att klienten får en typad felkropp. Ett {@code default}-svar gäller alla statuskoder som inte är
	 * beskrivna, och ersätter inte de lyckade svaren.
	 */
	@Bean
	OpenApiCustomizer felsvarArProblemDetail() {
		return openApi -> {
			if (openApi.getComponents() == null) {
				openApi.setComponents(new Components());
			}
			openApi.getComponents().addSchemas(PROBLEM_DETAIL, problemDetail());
			ApiResponse felsvar = new ApiResponse().description("Fel, som RFC 9457 ProblemDetail")
				.content(new Content().addMediaType("application/problem+json",
						new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + PROBLEM_DETAIL))));
			if (openApi.getPaths() != null) {
				openApi.getPaths()
					.values()
					.forEach(path -> path.readOperations()
						.forEach(operation -> operation.getResponses().addApiResponse("default", felsvar)));
			}
		};
	}

	/**
	 * Fälten som {@code ProblemDetail} alltid har är obligatoriska. {@code errors} följer bara med ett 400 för
	 * ogiltiga fält, och {@code mustChangePassword} bara med det 403 som skickar användaren till lösenordsbytet.
	 */
	private static Schema<?> problemDetail() {
		Schema<?> fel = new ObjectSchema().addProperty("field", new StringSchema())
			.addProperty("message", new StringSchema())
			.required(List.of("field", "message"));
		return new ObjectSchema().description("RFC 9457 ProblemDetail")
			.addProperty("type", new StringSchema())
			.addProperty("title", new StringSchema())
			.addProperty("status", new IntegerSchema().format("int32"))
			.addProperty("detail", new StringSchema())
			.addProperty("instance", new StringSchema())
			.addProperty("errors", new ArraySchema().items(fel))
			.addProperty("mustChangePassword", new BooleanSchema())
			.required(List.of("status"));
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
