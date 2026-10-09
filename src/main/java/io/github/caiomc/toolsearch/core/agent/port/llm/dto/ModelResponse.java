package io.github.caiomc.toolsearch.core.agent.port.llm.dto;

import java.util.List;

import io.github.caiomc.toolsearch.core.agent.domain.model.TokenUsage;

/**
 * Uma resposta do modelo: ações pedidas, texto (se houver) e tokens gastos.
 */
public record ModelResponse(
        List<ModelAction> actions,
        String text,
        TokenUsage usage
) {

    public ModelResponse {
        actions = List.copyOf(actions);
    }

    /**
     * Sem nada para a aplicação responder: o modelo terminou.
     */
    public boolean isFinal() {
        return this.actions.stream().noneMatch(action -> action instanceof ModelAction.ToolCall || action instanceof ModelAction.ToolSearchCall);
    }
}
