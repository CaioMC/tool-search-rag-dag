package io.github.caiomc.toolsearch.adapters.cli.scenario.model;

import java.util.Map;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário C7 de {@code docs/como-testar.md}: Chave inválida. Não precisa de chave.
 */
public final class ScenarioC7InvalidKey {

    private ScenarioC7InvalidKey() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "C7. Chave inválida",
                "termina em Erro 401 (chave recusada pela API)"
        );

        Scenario.command(
                Map.of("OPENAI_API_KEY", "errada"),
                "agente",
                "x"
        );
    }
}
