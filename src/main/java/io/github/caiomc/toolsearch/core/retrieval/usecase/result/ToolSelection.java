package io.github.caiomc.toolsearch.core.retrieval.usecase.result;

import java.util.List;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.RetrievalStrategyRag;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.SelectionOrigin;

/**
 * Resultado de uma busca de ferramentas: o que vai ser entregue ao modelo.
 *
 * <p>Com o DAG ligado, a lista já vem em ordem de execução (pré-requisitos primeiro).
 */
public record ToolSelection(
        String query,
        RetrievalStrategyRag strategyRag,
        boolean dagApplied,
        List<SelectedTool> tools
) {

    public ToolSelection {
        tools = List.copyOf(tools);
    }

    public List<ToolDefinition> definitions() {
        return this.tools.stream().map(SelectedTool::tool).toList();
    }

    public List<ToolName> names() {
        return this.tools.stream().map(SelectedTool::name).toList();
    }

    public long count(SelectionOrigin origin) {
        return this.tools.stream().filter(tool -> tool.origin() == origin).count();
    }
}
