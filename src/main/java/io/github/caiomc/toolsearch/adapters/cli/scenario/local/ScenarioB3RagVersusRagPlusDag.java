package io.github.caiomc.toolsearch.adapters.cli.scenario.local;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário B3 de {@code docs/como-testar.md}: RAG sozinho × RAG + DAG. Não precisa de chave.
 */
public final class ScenarioB3RagVersusRagPlusDag {

    private ScenarioB3RagVersusRagPlusDag() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "B3. RAG sozinho × RAG + DAG",
                "sem DAG nenhuma ferramenta de imposto aparece: o modelo não conseguiria emitir a nota",
                "com DAG, 1 ferramenta do RAG vira o conjunto completo e ordenado (6 em vez de 250)"
        );

        Scenario.command("buscar", "emita a nota fiscal do pedido 42", "--sem-dag");
        Scenario.command("buscar", "emita a nota fiscal do pedido 42", "--top", "1");
    }
}
