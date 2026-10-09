package io.github.caiomc.toolsearch.core.retrieval.application.rag;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.core.retrieval.usecase.result.SelectedTool;

import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.billingCatalog;
import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.name;
import static org.assertj.core.api.Assertions.assertThat;

class Bm25RetrieverRagTest {

    private final Bm25RetrieverRag retriever = new Bm25RetrieverRag(billingCatalog());

    @Test
    void ranksTheToolThatSharesMoreRareTermsFirst() {
        List<SelectedTool> result = this.retriever.retrieve("emita a nota fiscal", 2);

        assertThat(result).first().extracting(SelectedTool::name).isEqualTo(name("emitir_nota_fiscal"));
    }

    @Test
    void ignoresToolsWithoutAnyMatchingTerm() {
        List<SelectedTool> result = this.retriever.retrieve("folha de pagamento", 5);

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotUnderstandSynonyms() {
        List<SelectedTool> result = this.retriever.retrieve("tributo", 5);

        assertThat(result).as("BM25 casa palavras, não significados").isEmpty();
    }
}
