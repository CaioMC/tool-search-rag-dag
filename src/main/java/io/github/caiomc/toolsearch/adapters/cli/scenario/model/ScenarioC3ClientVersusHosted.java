package io.github.caiomc.toolsearch.adapters.cli.scenario.model;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário C3 de {@code docs/como-testar.md}: Tool search no cliente × hospedado, 250 ferramentas. Precisa de OPENAI_API_KEY (no .env ou exportada).
 */
public final class ScenarioC3ClientVersusHosted {

    private ScenarioC3ClientVersusHosted() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "C3. Tool search no cliente × hospedado, 250 ferramentas",
                "client: uma busca RAG+DAG traz a ferramenta e os pré-requisitos (marcados com *)",
                "hosted: várias buscas da OpenAI, um namespace por vez",
                "se houver 'BARRADA pelo DAG', o próximo passo executa o que faltava"
        );

        Scenario.command("agente", Scenario.NOTA_FISCAL, "--modo", "client");
        Scenario.command("agente", Scenario.NOTA_FISCAL, "--modo", "hosted");
    }
}
