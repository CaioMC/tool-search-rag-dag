package io.github.caiomc.toolsearch.adapters.cli.scenario.local;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário B1 de {@code docs/como-testar.md}: Tamanho do problema. Não precisa de chave.
 */
public final class ScenarioB1CatalogOverview {

    private ScenarioB1CatalogOverview() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "B1. Tamanho do problema",
                "250 ferramentas, 50 namespaces",
                "101 ferramentas têm pré-requisito: achar a 'parecida' não basta"
        );

        Scenario.command("catalogo");
    }
}
