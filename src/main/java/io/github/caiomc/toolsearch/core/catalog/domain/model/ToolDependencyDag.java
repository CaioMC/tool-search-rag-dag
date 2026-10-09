package io.github.caiomc.toolsearch.core.catalog.domain.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

import io.github.caiomc.toolsearch.core.catalog.domain.exception.CyclicDependencyException;

/**
 * O DAG (grafo acíclico dirigido) de dependências entre ferramentas.
 *
 * <p>Uma aresta {@code A -> B} quer dizer "A depende de B": B precisa ser executada antes de A.
 * O grafo responde três perguntas que o RAG sozinho não responde:
 * <ol>
 *     <li>{@link #withAllPrerequisites}: se o modelo vai usar A, quais outras ferramentas ele também precisa ter?</li>
 *     <li>{@link #executionOrder}: em que ordem executá-las?</li>
 *     <li>{@link #missingPrerequisites}: posso executar A agora, dado o que já foi executado?</li>
 * </ol>
 */
public final class ToolDependencyDag {

    private final Map<ToolName, Set<ToolName>> prerequisites;

    private ToolDependencyDag(Map<ToolName, Set<ToolName>> prerequisites) {
        this.prerequisites = prerequisites;
    }

    /**
     * Monta o grafo e falha se houver ciclo (A precisa de B que precisa de A nunca poderia rodar).
     */
    public static ToolDependencyDag of(ToolCatalog catalog) {
        Map<ToolName, Set<ToolName>> prerequisites = new HashMap<>();

        for (ToolDefinition tool : catalog.all()) {
            prerequisites.put(tool.name(), new TreeSet<>(tool.dependsOn()));
        }

        ToolDependencyDag dag = new ToolDependencyDag(prerequisites);

        dag.requireAcyclic();

        return dag;
    }

    public Set<ToolName> prerequisitesOf(ToolName tool) {
        return Set.copyOf(this.prerequisites.getOrDefault(tool, Set.of()));
    }

    /**
     * Fecho transitivo: as ferramentas pedidas mais todos os pré-requisitos, diretos e indiretos.
     */
    public Set<ToolName> withAllPrerequisites(Collection<ToolName> tools) {
        Set<ToolName> visited = new LinkedHashSet<>();
        Deque<ToolName> pending = new ArrayDeque<>(tools);

        while (!pending.isEmpty()) {
            ToolName current = pending.pop();

            if (visited.add(current)) {
                pending.addAll(this.prerequisites.getOrDefault(current, Set.of()));
            }
        }

        return visited;
    }

    /**
     * Ordenação topológica (algoritmo de Kahn) do fecho das ferramentas pedidas: pré-requisitos
     * primeiro. Empates saem em ordem alfabética, para o resultado ser determinístico.
     */
    public List<ToolName> executionOrder(Collection<ToolName> tools) {
        Set<ToolName> nodes = this.withAllPrerequisites(tools);
        Map<ToolName, Integer> pendingPrerequisites = new HashMap<>();
        Map<ToolName, List<ToolName>> dependents = new HashMap<>();

        for (ToolName node : nodes) {
            Set<ToolName> required = this.prerequisites.getOrDefault(node, Set.of());

            pendingPrerequisites.put(node, required.size());

            for (ToolName prerequisite : required) {
                dependents.computeIfAbsent(prerequisite, ignored -> new ArrayList<>()).add(node);
            }
        }

        PriorityQueue<ToolName> ready = new PriorityQueue<>();

        pendingPrerequisites.forEach((node, count) -> {
            if (count == 0) {
                ready.add(node);
            }
        });

        List<ToolName> order = new ArrayList<>();

        while (!ready.isEmpty()) {
            ToolName current = ready.poll();

            order.add(current);

            for (ToolName dependent : dependents.getOrDefault(current, List.of())) {
                int remaining = pendingPrerequisites.merge(dependent, -1, Integer::sum);

                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }

        return order;
    }

    /**
     * Pré-requisitos diretos de {@code tool} que ainda não foram executados.
     */
    public Set<ToolName> missingPrerequisites(
            ToolName tool,
            Set<ToolName> executed
    ) {
        Set<ToolName> missing = new TreeSet<>(this.prerequisites.getOrDefault(tool, Set.of()));

        missing.removeAll(executed);

        return missing;
    }

    public int edgeCount() {
        return this.prerequisites.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Maior cadeia de dependências que termina em {@code tool} (0 = sem pré-requisitos).
     */
    public int depthOf(ToolName tool) {
        Set<ToolName> required = this.prerequisites.getOrDefault(tool, Set.of());

        return required.stream().mapToInt(prerequisite -> this.depthOf(prerequisite) + 1).max().orElse(0);
    }

    private void requireAcyclic() {
        Map<ToolName, VisitState> states = new HashMap<>();

        for (ToolName node : new TreeSet<>(this.prerequisites.keySet())) {
            this.visit(
                    node,
                    states,
                    new ArrayList<>()
            );
        }
    }

    private void visit(
            ToolName node,
            Map<ToolName, VisitState> states,
            List<ToolName> path
    ) {
        VisitState state = states.get(node);

        if (state == VisitState.DONE) {
            return;
        }

        if (state == VisitState.VISITING) {
            List<ToolName> cycle = new ArrayList<>(path.subList(path.indexOf(node), path.size()));

            cycle.add(node);

            throw new CyclicDependencyException(cycle);
        }

        states.put(node, VisitState.VISITING);
        path.add(node);

        for (ToolName prerequisite : this.prerequisites.getOrDefault(node, Set.of())) {
            this.visit(
                    prerequisite,
                    states,
                    path
            );
        }

        path.remove(path.size() - 1);
        states.put(node, VisitState.DONE);
    }

    private enum VisitState {
        VISITING,
        DONE
    }
}
