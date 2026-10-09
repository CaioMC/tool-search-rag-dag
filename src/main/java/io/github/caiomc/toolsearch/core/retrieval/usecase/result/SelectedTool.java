package io.github.caiomc.toolsearch.core.retrieval.usecase.result;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.SelectionOrigin;

/**
 * Ferramenta escolhida para um pedido, com a nota do RAG ({@code scoreRag}) e o motivo da escolha.
 */
public record SelectedTool(
        ToolDefinition tool,
        double scoreRag,
        SelectionOrigin origin
) {

    public ToolName name() {
        return this.tool.name();
    }
}
