package io.github.caiomc.toolsearch.core.retrieval.application;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDependencyDag;
import io.github.caiomc.toolsearch.core.retrieval.application.dag.PrerequisiteExpanderDag;
import io.github.caiomc.toolsearch.core.retrieval.application.rag.Bm25RetrieverRag;
import io.github.caiomc.toolsearch.core.retrieval.application.rag.ToolSearchRag;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.RetrievalStrategyRag;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.SelectionOrigin;
import io.github.caiomc.toolsearch.core.retrieval.usecase.command.SelectToolsCommand;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.ToolSelection;

import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.billingCatalog;
import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.name;
import static org.assertj.core.api.Assertions.assertThat;

class SelectToolsServiceTest {

    private final ToolCatalog catalog = billingCatalog();

    private final SelectToolsService service = new SelectToolsService(
            new ToolSearchRag(List.of(new Bm25RetrieverRag(this.catalog))),
            new PrerequisiteExpanderDag(this.catalog, ToolDependencyDag.of(this.catalog))
    );

    @Test
    void withoutDagReturnsOnlyWhatRagFound() {
        ToolSelection selection = this.service.select(new SelectToolsCommand("emitir nota fiscal sefaz", RetrievalStrategyRag.BM25, 1, false));

        assertThat(selection.names()).containsExactly(name("emitir_nota_fiscal"));
        assertThat(selection.dagApplied()).isFalse();
    }

    @Test
    void withDagAddsPrerequisitesInExecutionOrder() {
        ToolSelection selection = this.service.select(new SelectToolsCommand("emitir nota fiscal sefaz", RetrievalStrategyRag.BM25, 1, true));

        assertThat(selection.names()).containsExactly(
                name("consultar_pedido_venda"),
                name("determinar_cfop_operacao"),
                name("calcular_impostos_operacao"),
                name("emitir_nota_fiscal")
        );

        assertThat(selection.dagApplied()).isTrue();
        assertThat(selection.count(SelectionOrigin.RAG)).isEqualTo(1);
        assertThat(selection.count(SelectionOrigin.DAG)).isEqualTo(3);
    }
}
