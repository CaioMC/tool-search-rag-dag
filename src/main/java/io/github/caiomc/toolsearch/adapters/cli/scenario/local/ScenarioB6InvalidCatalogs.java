package io.github.caiomc.toolsearch.adapters.cli.scenario.local;

import java.util.Map;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário B6 de {@code docs/como-testar.md}: Catálogos inválidos são recusados ao carregar. Não precisa de chave.
 */
public final class ScenarioB6InvalidCatalogs {

    private ScenarioB6InvalidCatalogs() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "B6. Catálogos inválidos são recusados ao carregar",
                "os três terminam em Erro, antes de qualquer chamada ao modelo",
                "a mensagem do ciclo mostra o caminho completo"
        );

        Scenario.command(
                Map.of("TOOLSEARCH_CATALOG", Scenario.CYCLE_CATALOG),
                "catalogo"
        );

        Scenario.command(
                Map.of("TOOLSEARCH_CATALOG", Scenario.UNKNOWN_DEPENDENCY_CATALOG),
                "catalogo"
        );

        Scenario.command(
                Map.of("TOOLSEARCH_CATALOG", "/nao/existe.json"),
                "catalogo"
        );
    }
}
