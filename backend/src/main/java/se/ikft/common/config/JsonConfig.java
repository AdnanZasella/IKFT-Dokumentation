package se.ikft.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.module.SimpleModule;

/**
 * Avvisar kontrolltecken (t.ex. NUL) i all text som läses från en JSON-förfrågan, redan när den läses.
 * Svaret blir 400. Databasen är bara sista skyddet, så att ett fel där inte blir en följd av vad klienten
 * skickade. Gäller varje strängfält i varje förfrågan, så att nya endpoints inte kan glömma det.
 */
@Configuration(proxyBeanMethods = false)
public class JsonConfig {

	@Bean
	JacksonModule rejectControlCharactersModule() {
		SimpleModule module = new SimpleModule("rejectControlCharacters");
		module.addDeserializer(String.class, new RejectingStringDeserializer());
		return module;
	}

	static class RejectingStringDeserializer extends StringDeserializer {

		@Override
		public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
			String value = super.deserialize(parser, context);
			if (value != null && ControlCharacters.containsDisallowed(value)) {
				// Själva texten tas inte med, eftersom den kan innehålla personuppgifter.
				throw context.weirdStringException("", String.class,
						"Texten innehåller kontrolltecken, till exempel NUL.");
			}
			return value;
		}

	}

}
