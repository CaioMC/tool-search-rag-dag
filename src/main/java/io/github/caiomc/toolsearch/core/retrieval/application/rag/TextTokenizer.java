package io.github.caiomc.toolsearch.core.retrieval.application.rag;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Quebra texto em português em termos comparáveis: minúsculas, sem acento, sem palavras vazias
 * e com um radical bem simples ("notas fiscais" → "not fiscal", "emita" → "emit").
 */
public final class TextTokenizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private static final Pattern SEPARATORS = Pattern.compile("[^a-z0-9]+");

    private static final Set<String> STOPWORDS = Set.of(
            "a", "o", "e", "as", "os", "de", "da", "do", "das", "dos", "em", "no", "na", "nos", "nas",
            "um", "uma", "uns", "umas", "para", "pra", "por", "pelo", "pela", "com", "sem", "ao", "aos",
            "que", "se", "ou", "meu", "minha", "seu", "sua", "qual", "quais", "este", "esta", "esse",
            "essa", "isso", "the", "of", "me", "eu", "preciso", "quero", "favor", "dele", "dela"
    );

    private TextTokenizer() {
    }

    public static List<String> tokenize(String text) {
        String normalized = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
        List<String> tokens = new ArrayList<>();

        for (String raw : SEPARATORS.split(normalized)) {
            if (raw.length() < 2 || STOPWORDS.contains(raw)) {
                continue;
            }

            tokens.add(stem(raw));
        }

        return tokens;
    }

    private static String stem(String token) {
        if (token.length() <= 3) {
            return token;
        }

        if (token.endsWith("ais")) {
            return token.substring(0, token.length() - 3) + "al";
        }

        if (token.endsWith("oes") || token.endsWith("aes")) {
            return token.substring(0, token.length() - 3) + "ao";
        }

        String singular = token.endsWith("s") ? token.substring(0, token.length() - 1) : token;

        return verbRoot(singular);
    }

    /**
     * Junta formas do mesmo verbo/substantivo: "emitir", "emita", "emite" → "emit";
     * "consultar", "consulta" → "consult".
     */
    private static String verbRoot(String token) {
        if (token.length() <= 4) {
            return token;
        }

        if (token.endsWith("ar") || token.endsWith("er") || token.endsWith("ir")) {
            return token.substring(0, token.length() - 2);
        }

        if (token.endsWith("a") || token.endsWith("e") || token.endsWith("o")) {
            return token.substring(0, token.length() - 1);
        }

        return token;
    }
}
