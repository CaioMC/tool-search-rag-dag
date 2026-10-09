package io.github.caiomc.toolsearch.core.retrieval.application.rag;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.RetrievalStrategyRag;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.SelectionOrigin;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.SelectedTool;

import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.billingCatalog;
import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.name;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolSearchRagTest {

    private final ToolCatalog catalog = billingCatalog();

    private final ToolSearchRag searchRag = new ToolSearchRag(List.of(new Bm25RetrieverRag(this.catalog)));

    @Test
    void usesTheRetrieverOfTheRequestedStrategy() {
        List<SelectedTool> foundByRag = this.searchRag.findSimilar("emitir nota fiscal sefaz", RetrievalStrategyRag.BM25, 1);

        assertThat(foundByRag).singleElement().satisfies(tool -> {
            assertThat(tool.name()).isEqualTo(name("emitir_nota_fiscal"));
            assertThat(tool.origin()).isEqualTo(SelectionOrigin.RAG);
            assertThat(tool.scoreRag()).isPositive();
        });
    }

    @Test
    void failsClearlyWhenStrategyIsNotConfigured() {
        assertThatThrownBy(() -> this.searchRag.findSimilar("nota", RetrievalStrategyRag.EMBEDDING, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMBEDDING");
    }
}
