package io.github.caiomc.toolsearch.core.catalog.application;

import java.util.Comparator;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDependencyDag;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.catalog.usecase.DescribeCatalogUseCase;
import io.github.caiomc.toolsearch.core.catalog.usecase.result.CatalogOverview;

/**
 * Números do catálogo e do DAG.
 */
public final class DescribeCatalogService implements DescribeCatalogUseCase {

    private final ToolCatalog catalog;

    private final ToolDependencyDag dag;

    public DescribeCatalogService(
            ToolCatalog catalog,
            ToolDependencyDag dag
    ) {
        this.catalog = catalog;
        this.dag = dag;
    }

    @Override
    public CatalogOverview describe() {
        ToolName deepest = this.catalog.all().stream()
                .map(ToolDefinition::name)
                .max(Comparator.comparingInt(this.dag::depthOf).thenComparing(Comparator.reverseOrder()))
                .orElseThrow(() -> new IllegalStateException("Catálogo vazio. Cadastre ao menos uma ferramenta."));

        return new CatalogOverview(
                this.catalog.size(),
                this.catalog.namespaces().size(),
                this.dag.edgeCount(),
                (int) this.catalog.all().stream().filter(ToolDefinition::hasPrerequisites).count(),
                deepest,
                this.dag.depthOf(deepest)
        );
    }
}
