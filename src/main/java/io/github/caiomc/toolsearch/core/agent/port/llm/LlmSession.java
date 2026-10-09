package io.github.caiomc.toolsearch.core.agent.port.llm;

import java.util.List;

import io.github.caiomc.toolsearch.core.agent.port.llm.dto.ModelResponse;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;

/**
 * Uma conversa com o modelo. O adaptador guarda o histórico; o core só reage aos pedidos.
 *
 * <p>Ciclo: {@link #sendToModel()} → adicionar ao histórico a resposta de cada ação pedida →
 * {@link #sendToModel()} de novo, até o modelo não pedir mais nada.
 *
 * <p>Só {@link #sendToModel()} chama o modelo. Os métodos {@code add...Output} não enviam nada:
 * guardam a resposta no histórico, que segue na próxima chamada.
 */
public interface LlmSession {

    /**
     * Chama o modelo (rede) com o histórico acumulado e devolve o que ele pediu/respondeu.
     */
    ModelResponse sendToModel();

    /**
     * Adiciona ao histórico a resposta de um {@code tool_search_call} do modo cliente
     * ({@code tool_search_output} com as ferramentas encontradas). Não chama o modelo.
     */
    void addToolSearchOutput(
            String callId,
            List<ToolDefinition> tools
    );

    /**
     * Adiciona ao histórico o resultado de uma chamada de função ({@code function_call_output}).
     * Não chama o modelo.
     */
    void addToolCallOutput(
            String callId,
            String output
    );
}
