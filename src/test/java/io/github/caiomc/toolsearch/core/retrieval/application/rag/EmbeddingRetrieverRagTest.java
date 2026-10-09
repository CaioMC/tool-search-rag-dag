package io.github.caiomc.toolsearch.core.retrieval.application.rag;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.ConceptEmbeddings;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.SelectedTool;

import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.billingCatalog;
import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.name;
import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingRetrieverRagTest {

    private final ConceptEmbeddings embeddings = new ConceptEmbeddings();

    private final EmbeddingRetrieverRag retriever = new EmbeddingRetrieverRag(billingCatalog(), this.embeddings);

    @Test
    void findsBySynonymWhereBm25Fails() {
        List<SelectedTool> result = this.retriever.retrieve("tributo", 1);

        assertThat(result).extracting(SelectedTool::name).containsExactly(name("calcular_impostos_operacao"));
    }

    @Test
    void indexesTheCatalogOnlyOnce() {
        this.retriever.retrieve("nf", 1);
        this.retriever.retrieve("tributo", 1);

        assertThat(this.embeddings.calls()).as("1 para o índice + 1 por pergunta").isEqualTo(3);
    }
}
