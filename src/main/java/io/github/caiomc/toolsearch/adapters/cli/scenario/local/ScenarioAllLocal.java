package io.github.caiomc.toolsearch.adapters.cli.scenario.local;

/**
 * Roda em sequência todos os cenários locais (B1 a B7) de {@code docs/como-testar.md}.
 * Não precisa de chave e não chama o modelo. Os erros de B6 e B7 são esperados.
 */
public final class ScenarioAllLocal {

    private ScenarioAllLocal() {
    }

    public static void main(String[] args) {
        ScenarioB1CatalogOverview.main(args);
        ScenarioB2ExecutionPlan.main(args);
        ScenarioB3RagVersusRagPlusDag.main(args);
        ScenarioB4Bm25Limit.main(args);
        ScenarioB5CustomCatalog.main(args);
        ScenarioB6InvalidCatalogs.main(args);
        ScenarioB7ArgumentErrors.main(args);
    }
}
