package io.github.caiomc.toolsearch.core.catalog.usecase.result;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;

/**
 * Números do catálogo, para entender o tamanho do problema.
 */
public record CatalogOverview(
        int toolCount,
        int namespaceCount,
        int dependencyCount,
        int toolsWithPrerequisites,
        ToolName deepestTool,
        int deepestChain
) {
}
