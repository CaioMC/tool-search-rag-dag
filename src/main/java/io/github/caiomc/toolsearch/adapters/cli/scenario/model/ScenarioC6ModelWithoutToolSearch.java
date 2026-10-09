package io.github.caiomc.toolsearch.adapters.cli.scenario.model;

import java.util.Map;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário C6 de {@code docs/como-testar.md}: Modelo sem tool search (gpt-4o). Precisa de OPENAI_API_KEY (no .env ou exportada).
 */
public final class ScenarioC6ModelWithoutToolSearch {

    private ScenarioC6ModelWithoutToolSearch() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "C6. Modelo sem tool search (gpt-4o)",
                "hosted falha: Tool 'tool_search' is not supported with gpt-4o",
                "preselecao funciona: é a alternativa RAG + DAG para esses modelos"
        );

        Scenario.command(
                Map.of("OPENAI_MODEL", "gpt-4o"),
                "agente",
                "Emita a nota fiscal do pedido 42",
                "--modo",
                "hosted"
        );

        Scenario.command(
                Map.of("OPENAI_MODEL", "gpt-4o"),
                "agente",
                "Emita a nota fiscal do pedido 42",
                "--modo",
                "preselecao"
        );
    }
}
