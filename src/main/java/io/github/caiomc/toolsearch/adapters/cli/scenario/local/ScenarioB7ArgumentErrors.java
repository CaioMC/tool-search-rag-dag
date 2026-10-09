package io.github.caiomc.toolsearch.adapters.cli.scenario.local;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário B7 de {@code docs/como-testar.md}: Erros de argumento. Não precisa de chave.
 */
public final class ScenarioB7ArgumentErrors {

    private ScenarioB7ArgumentErrors() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "B7. Erros de argumento",
                "cada comando termina em Erro com mensagem que diz o que corrigir",
                "argumento inválido é apontado antes de exigir a chave"
        );

        Scenario.command("plano", "nao_existe");
        Scenario.command("agente", "--modo", "client");
        Scenario.command("agente", "x", "--modo", "foo");
        Scenario.command("buscar", "x", "--estrategia", "foo");
    }
}
