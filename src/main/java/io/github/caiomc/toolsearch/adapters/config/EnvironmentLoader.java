package io.github.caiomc.toolsearch.adapters.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Monta as variáveis de ambiente da aplicação a partir de três fontes, da menor para a maior
 * prioridade:
 * <ol>
 *     <li>arquivo {@code .env} (caminho em {@code TOOLSEARCH_ENV_FILE}, padrão {@code .env} no diretório atual);</li>
 *     <li>variáveis de ambiente do sistema;</li>
 *     <li>valores fixados por quem chama (ex.: um cenário de teste que troca o modelo).</li>
 * </ol>
 * O {@code .env} existe para a chave não precisar ser exportada a cada terminal nem ir para o git.
 */
public final class EnvironmentLoader {

    public static final String ENV_FILE_VARIABLE = "TOOLSEARCH_ENV_FILE";

    public static final String DEFAULT_ENV_FILE = ".env";

    public Map<String, String> load(
            Map<String, String> system,
            Map<String, String> overrides
    ) {
        Path file = Path.of(overrides.getOrDefault(ENV_FILE_VARIABLE, system.getOrDefault(ENV_FILE_VARIABLE, DEFAULT_ENV_FILE)));
        Map<String, String> env = new HashMap<>(this.readDotEnv(file));

        env.putAll(system);
        env.putAll(overrides);

        return Map.copyOf(env);
    }

    /**
     * Lê linhas {@code CHAVE=valor}. Aceita comentários ({@code #}), linhas em branco, prefixo
     * {@code export} e valor entre aspas simples ou duplas. Arquivo ausente = nenhuma variável.
     */
    public Map<String, String> readDotEnv(Path file) {
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }

        Map<String, String> values = new HashMap<>();

        try {
            for (String raw : Files.readAllLines(file)) {
                String line = raw.strip();

                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                    continue;
                }

                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).strip();
                }

                int separator = line.indexOf('=');

                values.put(line.substring(0, separator).strip(), unquote(line.substring(separator + 1).strip()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o arquivo de ambiente " + file + ": " + e.getMessage(), e);
        }

        return values;
    }

    private static String unquote(String value) {
        boolean quoted = value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"));

        return quoted ? value.substring(1, value.length() - 1) : value;
    }
}
