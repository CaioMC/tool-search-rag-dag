package io.github.caiomc.toolsearch.core.retrieval.application.dag;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDependencyDag;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.SelectionOrigin;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.SelectedTool;

/**
 * Passo DAG da seleção: recebe o que o RAG achou, acrescenta os pré-requisitos (diretos e
 * indiretos) que faltaram e devolve tudo em ordem de execução, pré-requisitos primeiro.
 *
 * <p>Ferramentas trazidas só pelo DAG saem com origem {@link SelectionOrigin#DAG} e nota 0.
 */
public final class PrerequisiteExpanderDag {

    private final ToolCatalog catalog;

    private final ToolDependencyDag dag;

    public PrerequisiteExpanderDag(
            ToolCatalog catalog,
            ToolDependencyDag dag
    ) {
        this.catalog = catalog;
        this.dag = dag;
    }

    public List<SelectedTool> addPrerequisitesInOrder(List<SelectedTool> foundByRag) {
        Map<ToolName, SelectedTool> foundByRagByName = foundByRag.stream().collect(Collectors.toMap(SelectedTool::name, Function.identity()));
        List<ToolName> executionOrderDag = this.dag.executionOrder(foundByRagByName.keySet());

        return executionOrderDag.stream()
                .map(name -> foundByRagByName.getOrDefault(name, this.addedByDag(name)))
                .toList();
    }

    private SelectedTool addedByDag(ToolName name) {
        return new SelectedTool(
                this.catalog.require(name),
                0,
                SelectionOrigin.DAG
        );
    }
}
