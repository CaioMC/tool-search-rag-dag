package io.github.caiomc.toolsearch.adapters.catalog.json.dto;

import java.util.List;

/**
 * Formato do arquivo {@code erp-tools.json}.
 */
public record CatalogJson(
        List<NamespaceJson> namespaces,
        List<ToolJson> tools
) {
}
