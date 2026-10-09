package io.github.caiomc.toolsearch.core.catalog.usecase;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.catalog.usecase.result.ExecutionPlan;

/**
 * Mostra, pelo DAG, o que precisa rodar antes de uma ferramenta e em que ordem.
 */
public interface PlanExecutionUseCase {

    ExecutionPlan plan(ToolName target);
}
