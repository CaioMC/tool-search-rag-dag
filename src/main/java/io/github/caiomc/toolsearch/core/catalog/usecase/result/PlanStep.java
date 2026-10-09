package io.github.caiomc.toolsearch.core.catalog.usecase.result;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;

/**
 * Um passo do plano: a ferramenta e os pré-requisitos diretos dela.
 */
public record PlanStep(
        ToolName tool,
        Set<ToolName> prerequisites
) {

    public PlanStep {
        prerequisites = Collections.unmodifiableSortedSet(new TreeSet<>(prerequisites));
    }
}
