package io.github.caiomc.toolsearch.adapters.cli.scenario.model;

import java.util.Map;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário C9 de {@code docs/como-testar.md}: Tool search no cliente (RAG + DAG) com 1000 ferramentas. Precisa de OPENAI_API_KEY (no .env ou exportada).
 *
 * <p>Contraparte do C8: o mesmo catálogo de 1000 ferramentas e o mesmo pedido, mas quem responde ao
 * {@code tool_search} é a aplicação, com RAG (BM25, top 5) + DAG. A 1ª chamada leva só a ferramenta
 * de busca, qualquer que seja o tamanho do catálogo.
 */
public final class ScenarioC9ClientThousandTools {

    private ScenarioC9ClientThousandTools() {
    }

    public static void main(String[] args) {
        Map<String, String> thousandTools = Map.of("TOOLSEARCH_CATALOG", Scenario.THOUSAND_TOOLS_CATALOG);

        Scenario.title(
                "C9. Tool search no cliente (RAG + DAG) com 1000 ferramentas",
                "0 ferramentas na 1ª chamada, mesmo com 1000 no catálogo",
                "uma busca RAG+DAG traz emitir_nota_fiscal com os pré-requisitos (marcados com *)",
                "compare tokens, chamadas, tempo e se a nota foi emitida com o C8 (hosted, mesmo catálogo)"
        );

        Scenario.command(
                thousandTools,
                "buscar",
                Scenario.NOTA_FISCAL
        );

        Scenario.command(
                thousandTools,
                "comparar",
                Scenario.NOTA_FISCAL,
                "--modos",
                "client",
                "--detalhes"
        );
    }
}
