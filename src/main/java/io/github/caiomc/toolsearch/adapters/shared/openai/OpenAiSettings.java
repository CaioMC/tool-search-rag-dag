package io.github.caiomc.toolsearch.adapters.shared.openai;

import java.time.Duration;
import java.util.Map;

import static java.util.Objects.isNull;

/**
 * Configuração de acesso à OpenAI (ou a qualquer servidor compatível com a API dela, como um proxy LiteLLM).
 * A chave vem só de variável de ambiente (ou do {@code .env}) e nunca aparece no {@link #toString()}.
 * Variável vazia vale o mesmo que ausente, assim um {@code .env} com {@code OPENAI_BASE_URL=} usa a
 * API oficial.
 */
public record OpenAiSettings(
        String baseUrl,
        String apiKey,
        String model,
        String embeddingModel,
        Duration timeout
) {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";

    public static final String DEFAULT_MODEL = "gpt-5.4-mini";

    public static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";

    public OpenAiSettings {
        if (isNull(apiKey) || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY não definida. Copie .env.example para .env e preencha a chave (https://platform.openai.com/api-keys), ou rode: export OPENAI_API_KEY=<sua-chave>");
        }

        baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public static OpenAiSettings fromEnvironment(Map<String, String> env) {
        return new OpenAiSettings(
                valueOrDefault(env, "OPENAI_BASE_URL", DEFAULT_BASE_URL),
                env.get("OPENAI_API_KEY"),
                valueOrDefault(env, "OPENAI_MODEL", DEFAULT_MODEL),
                valueOrDefault(env, "OPENAI_EMBEDDING_MODEL", DEFAULT_EMBEDDING_MODEL),
                Duration.ofSeconds(Long.parseLong(valueOrDefault(env, "OPENAI_TIMEOUT_SECONDS", "120")))
        );
    }

    private static String valueOrDefault(
            Map<String, String> env,
            String name,
            String defaultValue
    ) {
        String value = env.get(name);

        return isNull(value) || value.isBlank() ? defaultValue : value.strip();
    }

    @Override
    public String toString() {
        return "OpenAiSettings[baseUrl=" + this.baseUrl + ", model=" + this.model + ", embeddingModel=" + this.embeddingModel + ", apiKey=***]";
    }
}
