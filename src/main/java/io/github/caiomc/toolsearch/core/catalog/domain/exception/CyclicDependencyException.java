package io.github.caiomc.toolsearch.core.catalog.domain.exception;

import java.util.List;
import java.util.stream.Collectors;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;

/**
 * O catálogo tem um ciclo de dependências: nenhuma das ferramentas do ciclo poderia ser executada.
 */
public class CyclicDependencyException extends RuntimeException {

    private final List<ToolName> cycle;

    public CyclicDependencyException(List<ToolName> cycle) {
        super("Ciclo de dependências entre ferramentas: " + cycle.stream().map(ToolName::value).collect(Collectors.joining(" -> ")) + ". Remova uma das arestas do ciclo.");
        this.cycle = List.copyOf(cycle);
    }

    public List<ToolName> cycle() {
        return this.cycle;
    }
}
