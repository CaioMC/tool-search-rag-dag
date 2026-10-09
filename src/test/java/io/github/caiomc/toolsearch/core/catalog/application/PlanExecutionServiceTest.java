package io.github.caiomc.toolsearch.core.catalog.application;

import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.core.catalog.domain.exception.UnknownToolException;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDependencyDag;
import io.github.caiomc.toolsearch.core.catalog.usecase.result.CatalogOverview;
import io.github.caiomc.toolsearch.core.catalog.usecase.result.ExecutionPlan;
import io.github.caiomc.toolsearch.core.catalog.usecase.result.PlanStep;

import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.billingCatalog;
import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.name;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanExecutionServiceTest {

    private final ToolCatalog catalog = billingCatalog();

    private final ToolDependencyDag dag = ToolDependencyDag.of(this.catalog);

    @Test
    void planListsEachStepWithItsDirectPrerequisites() {
        ExecutionPlan plan = new PlanExecutionService(this.catalog, this.dag).plan(name("emitir_nota_fiscal"));

        assertThat(plan.steps()).extracting(PlanStep::tool).last().isEqualTo(name("emitir_nota_fiscal"));
        assertThat(plan.steps().get(plan.steps().size() - 1).prerequisites()).containsExactly(name("calcular_impostos_operacao"), name("consultar_pedido_venda"));
    }

    @Test
    void unknownToolFails() {
        assertThatThrownBy(() -> new PlanExecutionService(this.catalog, this.dag).plan(name("nao_existe"))).isInstanceOf(UnknownToolException.class);
    }

    @Test
    void overviewFindsTheDeepestChain() {
        CatalogOverview overview = new DescribeCatalogService(this.catalog, this.dag).describe();

        assertThat(overview.toolCount()).isEqualTo(5);
        assertThat(overview.dependencyCount()).isEqualTo(3);
        assertThat(overview.deepestTool()).isEqualTo(name("emitir_nota_fiscal"));
        assertThat(overview.deepestChain()).isEqualTo(2);
    }
}
