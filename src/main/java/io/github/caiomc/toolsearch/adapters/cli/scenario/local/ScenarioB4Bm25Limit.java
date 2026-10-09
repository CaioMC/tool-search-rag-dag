package io.github.caiomc.toolsearch.adapters.cli.scenario.local;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário B4 de {@code docs/como-testar.md}: Limite do BM25. Não precisa de chave.
 */
public final class ScenarioB4Bm25Limit {

    private ScenarioB4Bm25Limit() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "B4. Limite do BM25",
                "gerar_balancete aparece (casou a palavra 'gerar')",
                "emitir_nota_fiscal não aparece: BM25 casa palavras, não significado (compare com C1)"
        );

        Scenario.command("buscar", "gerar NF-e para o pedido 42", "--sem-dag");
    }
}
