package io.github.caiomc.toolsearch.adapters.cli.scenario.local;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário B2 de {@code docs/como-testar.md}: O DAG decide a ordem. Não precisa de chave.
 */
public final class ScenarioB2ExecutionPlan {

    private ScenarioB2ExecutionPlan() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "B2. O DAG decide a ordem",
                "todo pré-requisito aparece antes de quem depende dele",
                "consultar_produto e determinar_cfop_operacao entram pelo fecho transitivo"
        );

        Scenario.command("plano", "emitir_nota_fiscal");
    }
}
