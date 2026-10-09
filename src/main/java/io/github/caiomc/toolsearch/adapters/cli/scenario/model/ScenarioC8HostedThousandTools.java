package io.github.caiomc.toolsearch.adapters.cli.scenario.model;

import java.util.Map;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário C8 de {@code docs/como-testar.md}: Tool search hospedado com 1000 ferramentas. Precisa de OPENAI_API_KEY (no .env ou exportada).
 *
 * <p>Isola o tool search da OpenAI: as 1000 funções vão com {@code defer_loading} em 200 namespaces
 * e a própria OpenAI decide o que carregar, sem o RAG + DAG no cliente. Mede se a API aceita esse
 * volume e quanto custa (tokens, chamadas, buscas e tempo). A trava do DAG em tempo de execução
 * continua ligada, como em todos os modos.
 */
public final class ScenarioC8HostedThousandTools {

    private ScenarioC8HostedThousandTools() {
    }

    public static void main(String[] args) {
        Map<String, String> thousandTools = Map.of("TOOLSEARCH_CATALOG", Scenario.THOUSAND_TOOLS_CATALOG);

        Scenario.title(
                "C8. Tool search hospedado (OpenAI) com 1000 ferramentas",
                "o catálogo tem 1000 ferramentas em 200 namespaces (as 250 de sempre + 30 domínios extras)",
                "a API aceita as 1000 funções deferidas, ou responde com erro de limite",
                "quantos tool_search a OpenAI faz até achar as 6 ferramentas da nota fiscal",
                "compare tokens de entrada, chamadas e tempo com o hosted de 250 (C3 e docs/resultados.md)"
        );

        Scenario.command(
                thousandTools,
                "catalogo"
        );

        Scenario.command(
                thousandTools,
                "comparar",
                Scenario.NOTA_FISCAL,
                "--modos",
                "hosted",
                "--detalhes"
        );
    }
}
