package io.github.caiomc.toolsearch.adapters.cli.scenario.model;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário C4 de {@code docs/como-testar.md}: Comparação dos 5 modos. Precisa de OPENAI_API_KEY (no .env ou exportada).
 */
public final class ScenarioC4CompareModes {

    private ScenarioC4CompareModes() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "C4. Comparação dos 5 modos",
                "todas e prompt com muito mais tokens de entrada que client e preselecao",
                "todos os modos concluem a tarefa; se algum não concluir, veja o passo a passo dele",
                "leva alguns minutos: são 7 execuções do agente"
        );

        Scenario.command("comparar", Scenario.NOTA_FISCAL, "--detalhes");
        Scenario.command("comparar", "Pague o título 77 do fornecedor", "--modos", "client,preselecao");
    }
}
