package io.github.caiomc.toolsearch.core.retrieval.application.rag;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.caiomc.toolsearch.core.retrieval.domain.enums.RetrievalStrategyRag;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.SelectedTool;

/**
 * Passo RAG da seleção: escolhe o {@link ToolRetrieverRag} da estratégia pedida (BM25 ou
 * embeddings) e devolve as top-k ferramentas mais parecidas com o pedido, da maior nota para a
 * menor. Não sabe nada de dependências; isso é o passo DAG.
 */
public final class ToolSearchRag {

    private final Map<RetrievalStrategyRag, ToolRetrieverRag> retrieversRag;

    public ToolSearchRag(List<ToolRetrieverRag> retrieversRag) {
        this.retrieversRag = new EnumMap<>(RetrievalStrategyRag.class);

        for (ToolRetrieverRag retrieverRag : retrieversRag) {
            this.retrieversRag.put(retrieverRag.strategy(), retrieverRag);
        }
    }

    public List<SelectedTool> findSimilar(
            String query,
            RetrievalStrategyRag strategyRag,
            int topKRag
    ) {
        ToolRetrieverRag retrieverRag = Optional
                .ofNullable(this.retrieversRag.get(strategyRag))
                .orElseThrow(() -> new IllegalStateException("Estratégia de busca " + strategyRag + " não configurada."));

        return retrieverRag.retrieve(query, topKRag);
    }
}
