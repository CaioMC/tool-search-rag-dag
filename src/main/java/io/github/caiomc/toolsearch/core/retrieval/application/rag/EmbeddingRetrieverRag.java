package io.github.caiomc.toolsearch.core.retrieval.application.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.RetrievalStrategyRag;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.SelectionOrigin;
import io.github.caiomc.toolsearch.core.retrieval.domain.model.Embedding;
import io.github.caiomc.toolsearch.core.retrieval.port.embedding.EmbeddingPort;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.SelectedTool;

import static java.util.Objects.isNull;

/**
 * RAG semântico: indexa a descrição de cada ferramenta como vetor e, a cada pergunta, devolve
 * as ferramentas com maior similaridade de cosseno.
 *
 * <p>O índice é montado na primeira busca (uma chamada de embeddings para o catálogo inteiro)
 * e fica em memória. Em produção ele iria para um banco vetorial (pgvector, OpenSearch...).
 */
public final class EmbeddingRetrieverRag implements ToolRetrieverRag {

    private final ToolCatalog catalog;

    private final EmbeddingPort embeddings;

    private List<IndexedTool> index;

    public EmbeddingRetrieverRag(
            ToolCatalog catalog,
            EmbeddingPort embeddings
    ) {
        this.catalog = catalog;
        this.embeddings = embeddings;
    }

    @Override
    public RetrievalStrategyRag strategy() {
        return RetrievalStrategyRag.EMBEDDING;
    }

    @Override
    public List<SelectedTool> retrieve(
            String query,
            int topKRag
    ) {
        List<IndexedTool> indexed = this.index();
        Embedding queryVector = this.embeddings.embed(List.of(query)).get(0);

        return indexed.stream()
                .map(entry -> new SelectedTool(entry.tool(), entry.vector().cosineSimilarity(queryVector), SelectionOrigin.RAG))
                .sorted(Comparator.comparingDouble(SelectedTool::scoreRag).reversed().thenComparing(SelectedTool::name))
                .limit(topKRag)
                .toList();
    }

    private synchronized List<IndexedTool> index() {
        if (isNull(this.index)) {
            this.index = this.buildIndex();
        }

        return this.index;
    }

    private List<IndexedTool> buildIndex() {
        List<ToolDefinition> tools = this.catalog.all();
        List<Embedding> vectors = this.embeddings.embed(tools.stream().map(ToolDefinition::searchableText).toList());
        List<IndexedTool> built = new ArrayList<>();

        for (int i = 0; i < tools.size(); i++) {
            built.add(new IndexedTool(tools.get(i), vectors.get(i)));
        }

        return List.copyOf(built);
    }

    private record IndexedTool(
            ToolDefinition tool,
            Embedding vector
    ) {
    }
}
