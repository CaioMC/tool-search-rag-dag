package io.github.caiomc.toolsearch.core.retrieval.application.rag;

import java.util.List;

import io.github.caiomc.toolsearch.core.retrieval.domain.enums.RetrievalStrategyRag;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.SelectedTool;

/**
 * A parte "R" do RAG: dado um texto, devolve as ferramentas mais parecidas, da maior nota para
 * a menor. Não sabe nada de dependências; isso é papel do DAG.
 */
public interface ToolRetrieverRag {

    RetrievalStrategyRag strategy();

    List<SelectedTool> retrieve(
            String query,
            int topKRag
    );
}
