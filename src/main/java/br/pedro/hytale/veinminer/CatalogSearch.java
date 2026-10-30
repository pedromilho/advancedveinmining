package br.pedro.hytale.veinminer;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class CatalogSearch {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WORDS = Pattern.compile("\\s+");

    static String normalize(String text) {
        return MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("")
            .toLowerCase(Locale.ROOT).replace('_', ' ').strip();
    }

    static boolean matches(String normalizedText, String normalizedQuery) {
        return matches(normalizedText, words(normalizedQuery));
    }

    static String[] words(String normalizedQuery) { return WORDS.split(normalizedQuery); }

    static boolean matches(String normalizedText, String[] words) {
        for (String word : words) {
            if (!normalizedText.contains(word)) return false;
        }
        return true;
    }
}
