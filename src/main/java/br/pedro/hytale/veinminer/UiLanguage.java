package br.pedro.hytale.veinminer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

enum UiLanguage {
    ENGLISH("en-US", "en"), PORTUGUESE("pt-BR", "pt");

    final String assetLocale;
    private final Properties messages;

    UiLanguage(String assetLocale, String resource) {
        this.assetLocale = assetLocale;
        messages = new Properties();
        try (var stream = UiLanguage.class.getResourceAsStream("/lang/" + resource + ".properties")) {
            if (stream == null) throw new IllegalStateException("Missing language resource: " + resource);
            messages.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot load language resource: " + resource, error);
        }
    }

    static UiLanguage from(String locale) {
        if (locale == null) return ENGLISH;
        String normalized = locale.strip().replace('_', '-').toLowerCase(Locale.ROOT);
        return normalized.equals("pt") || normalized.startsWith("pt-") ? PORTUGUESE : ENGLISH;
    }

    String text(String key, Object... arguments) {
        String template = messages.getProperty(key, ENGLISH.messages.getProperty(key));
        if (template == null) throw new IllegalArgumentException("Unknown translation key: " + key);
        return arguments.length == 0 ? template : String.format(Locale.ROOT, template, arguments);
    }

    Set<String> keys() { return Set.copyOf(messages.stringPropertyNames()); }
}
