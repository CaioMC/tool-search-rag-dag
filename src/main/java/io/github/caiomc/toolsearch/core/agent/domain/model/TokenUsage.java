package io.github.caiomc.toolsearch.core.agent.domain.model;

/**
 * Tokens consumidos, como a API devolve em {@code usage}.
 */
public record TokenUsage(
        long inputTokens,
        long cachedInputTokens,
        long outputTokens
) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0);

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(
                this.inputTokens + other.inputTokens,
                this.cachedInputTokens + other.cachedInputTokens,
                this.outputTokens + other.outputTokens
        );
    }
}
