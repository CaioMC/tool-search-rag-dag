package io.github.caiomc.toolsearch.core.catalog.usecase.result;

import java.util.List;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;

/**
 * Ordem em que as ferramentas precisam rodar para que {@code target} possa ser executada.
 * O último passo é sempre o próprio {@code target}.
 */
public record ExecutionPlan(
        ToolName target,
        List<PlanStep> steps
) {

    public ExecutionPlan {
        steps = List.copyOf(steps);
    }
}
