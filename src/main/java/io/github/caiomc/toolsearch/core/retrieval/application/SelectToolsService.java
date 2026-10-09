package io.github.caiomc.toolsearch.core.retrieval.application;

import java.util.List;

import io.github.caiomc.toolsearch.core.retrieval.application.dag.PrerequisiteExpanderDag;
import io.github.caiomc.toolsearch.core.retrieval.application.rag.ToolSearchRag;
import io.github.caiomc.toolsearch.core.retrieval.usecase.SelectToolsUseCase;
import io.github.caiomc.toolsearch.core.retrieval.usecase.command.SelectToolsCommand;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.SelectedTool;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.ToolSelection;

/**
 * O ponto único da seleção de ferramentas. Toda busca (comando {@code buscar}, modo
 * {@code preselecao} e o tool search no cliente) passa por aqui, em dois passos:
 * <ol>
 *     <li><b>RAG</b> ({@link ToolSearchRag}): as top-k ferramentas mais parecidas com o pedido;</li>
 *     <li><b>DAG</b> ({@link PrerequisiteExpanderDag}): acrescenta os pré-requisitos que o RAG não
 *     trouxe e ordena tudo para execução. Pode ser desligado ({@code useDag = false}).</li>
 * </ol>
 */
public final class SelectToolsService implements SelectToolsUseCase {

    private final ToolSearchRag searchRag;

    private final PrerequisiteExpanderDag expanderDag;

    public SelectToolsService(
            ToolSearchRag searchRag,
            PrerequisiteExpanderDag expanderDag
    ) {
        this.searchRag = searchRag;
        this.expanderDag = expanderDag;
    }

    @Override
    public ToolSelection select(SelectToolsCommand command) {
        List<SelectedTool> foundByRag = this.searchRag.findSimilar(
                command.query(),
                command.strategyRag(),
                command.topKRag()
        );

        if (!command.useDag()) {
            return this.selection(command, false, foundByRag);
        }

        List<SelectedTool> completedByDag = this.expanderDag.addPrerequisitesInOrder(foundByRag);

        return this.selection(command, true, completedByDag);
    }

    private ToolSelection selection(
            SelectToolsCommand command,
            boolean dagApplied,
            List<SelectedTool> tools
    ) {
        return new ToolSelection(
                command.query(),
                command.strategyRag(),
                dagApplied,
                tools
        );
    }
}
