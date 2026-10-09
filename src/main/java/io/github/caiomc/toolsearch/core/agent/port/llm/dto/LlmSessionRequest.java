package io.github.caiomc.toolsearch.core.agent.port.llm.dto;

import java.util.List;

import io.github.caiomc.toolsearch.core.agent.domain.enums.ToolExposure;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;

/**
 * Como abrir a conversa: instruções, pedido do usuário, ferramentas e como expô-las.
 *
 * <p>{@code catalog} é usado pelo adaptador para descrever namespaces no modo hospedado.
 */
public record LlmSessionRequest(
        String instructions,
        String userPrompt,
        ToolExposure exposure,
        List<ToolDefinition> tools,
        ToolCatalog catalog
) {

    public LlmSessionRequest {
        tools = List.copyOf(tools);
    }
}
