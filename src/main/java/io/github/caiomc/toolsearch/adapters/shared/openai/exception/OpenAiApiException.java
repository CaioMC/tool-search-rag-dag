package io.github.caiomc.toolsearch.adapters.shared.openai.exception;

/**
 * A API respondeu com erro (status HTTP diferente de 2xx).
 */
public class OpenAiApiException extends RuntimeException {

    private final int status;

    public OpenAiApiException(
            int status,
            String path,
            String body
    ) {
        super("OpenAI respondeu " + status + " em " + path + ": " + body);
        this.status = status;
    }

    public int status() {
        return this.status;
    }
}
