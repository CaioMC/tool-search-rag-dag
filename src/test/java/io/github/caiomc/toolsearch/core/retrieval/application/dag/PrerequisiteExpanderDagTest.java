package io.github.caiomc.toolsearch.core.retrieval.application.dag;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDependencyDag;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.SelectionOrigin;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.SelectedTool;

import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.billingCatalog;
import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.name;
import static org.assertj.core.api.Assertions.assertThat;

class PrerequisiteExpanderDagTest {

    private final ToolCatalog catalog = billingCatalog();

    private final PrerequisiteExpanderDag expanderDag = new PrerequisiteExpanderDag(this.catalog, ToolDependencyDag.of(this.catalog));

    @Test
    void addsMissingPrerequisitesFirstAndKeepsTheRagScore() {
        SelectedTool foundByRag = new SelectedTool(this.catalog.require(name("emitir_nota_fiscal")), 2.5, SelectionOrigin.RAG);

        List<SelectedTool> completedByDag = this.expanderDag.addPrerequisitesInOrder(List.of(foundByRag));

        assertThat(completedByDag).extracting(SelectedTool::name).containsExactly(
                name("consultar_pedido_venda"),
                name("determinar_cfop_operacao"),
                name("calcular_impostos_operacao"),
                name("emitir_nota_fiscal")
        );

        assertThat(completedByDag).filteredOn(tool -> tool.origin() == SelectionOrigin.DAG).hasSize(3).allMatch(tool -> tool.scoreRag() == 0);
        assertThat(completedByDag.get(3)).isEqualTo(foundByRag);
    }

    @Test
    void toolWithoutPrerequisitesStaysAlone() {
        SelectedTool foundByRag = new SelectedTool(this.catalog.require(name("consultar_pedido_venda")), 1.0, SelectionOrigin.RAG);

        assertThat(this.expanderDag.addPrerequisitesInOrder(List.of(foundByRag))).containsExactly(foundByRag);
    }
}
