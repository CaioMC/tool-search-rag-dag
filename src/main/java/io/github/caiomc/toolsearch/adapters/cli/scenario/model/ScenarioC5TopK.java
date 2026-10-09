package io.github.caiomc.toolsearch.adapters.cli.scenario.model;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário C5 de {@code docs/como-testar.md}: O top-k importa. Precisa de OPENAI_API_KEY (no .env ou exportada).
 */
public final class ScenarioC5TopK {

    private ScenarioC5TopK() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "C5. O top-k importa",
                "--top 3: efetuar_pagamento_titulo_pagar fica de fora e o modelo diz que não tem como pagar",
                "--top 8: ela entra e o DAG acrescenta projetar_fluxo_caixa e consultar_conta_bancaria"
        );

        Scenario.command("comparar", "Pague o título 77 do fornecedor", "--modos", "client,preselecao", "--estrategia", "embedding", "--top", "3");
        Scenario.command("agente", "Pague o título 77 do fornecedor", "--modo", "client", "--estrategia", "embedding", "--top", "8");
    }
}
