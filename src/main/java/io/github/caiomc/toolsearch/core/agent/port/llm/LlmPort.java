package io.github.caiomc.toolsearch.core.agent.port.llm;

import io.github.caiomc.toolsearch.core.agent.port.llm.dto.LlmSessionRequest;

/**
 * Acesso ao modelo de linguagem. Cada conversa é uma {@link LlmSession}.
 */
public interface LlmPort {

    /**
     * Prepara uma conversa nova (ainda sem chamar o modelo).
     */
    LlmSession startConversation(LlmSessionRequest request);

    String modelName();
}
