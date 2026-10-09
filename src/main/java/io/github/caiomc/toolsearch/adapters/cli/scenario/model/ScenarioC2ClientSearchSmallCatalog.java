package io.github.caiomc.toolsearch.adapters.cli.scenario.model;

import java.util.Map;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário C2 de {@code docs/como-testar.md}: Tool search no cliente, catálogo pequeno. Precisa de OPENAI_API_KEY (no .env ou exportada).
 */
public final class ScenarioC2ClientSearchSmallCatalog {

    private ScenarioC2ClientSearchSmallCatalog() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "C2. Tool search no cliente, catálogo pequeno",
                "0 ferramentas na 1ª chamada: o modelo começa só com a busca",
                "o passo 1 é o tool_search_call, com o objetivo escrito pelo modelo",
                "as ferramentas executam na ordem do DAG"
        );

        Scenario.command(
                Map.of("TOOLSEARCH_CATALOG", Scenario.MINI_CATALOG),
                "agente",
                "Aprove o pedido 10",
                "--modo",
                "client"
        );
    }
}
