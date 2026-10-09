package io.github.caiomc.toolsearch.adapters.cli.scenario.local;

import java.util.Map;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário B5 de {@code docs/como-testar.md}: Catálogo próprio. Não precisa de chave.
 */
public final class ScenarioB5CustomCatalog {

    private ScenarioB5CustomCatalog() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "B5. Catálogo próprio",
                "3 ferramentas, 2 dependências",
                "ordem consultar_pedido → analisar_credito → aprovar_pedido"
        );

        Scenario.command(
                Map.of("TOOLSEARCH_CATALOG", Scenario.MINI_CATALOG),
                "catalogo"
        );

        Scenario.command(
                Map.of("TOOLSEARCH_CATALOG", Scenario.MINI_CATALOG),
                "plano",
                "aprovar_pedido"
        );
    }
}
