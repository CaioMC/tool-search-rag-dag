package io.github.caiomc.toolsearch.core.catalog.application;

import java.util.List;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDependencyDag;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.catalog.usecase.PlanExecutionUseCase;
import io.github.caiomc.toolsearch.core.catalog.usecase.result.ExecutionPlan;
import io.github.caiomc.toolsearch.core.catalog.usecase.result.PlanStep;

/**
 * Ordem de execução de uma ferramenta segundo o DAG.
 */
public final class PlanExecutionService implements PlanExecutionUseCase {

    private final ToolCatalog catalog;

    private final ToolDependencyDag dag;

    public PlanExecutionService(
            ToolCatalog catalog,
            ToolDependencyDag dag
    ) {
        this.catalog = catalog;
        this.dag = dag;
    }

    @Override
    public ExecutionPlan plan(ToolName target) {
        this.catalog.require(target);

        List<PlanStep> steps = this.dag
                .executionOrder(List.of(target))
                .stream()
                .map(tool -> new PlanStep(tool, this.dag.prerequisitesOf(tool)))
                .toList();

        return new ExecutionPlan(target, steps);
    }
}
