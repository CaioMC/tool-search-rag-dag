package io.github.caiomc.toolsearch.core.catalog.domain.model;

import static java.util.Objects.isNull;

/**
 * Agrupamento de ferramentas. No tool search hospedado, o modelo vê só o nome e a descrição
 * do namespace e pede para carregar as ferramentas dele quando precisar.
 */
public record ToolNamespace(
        String name,
        String description
) {

    public ToolNamespace {
        if (isNull(name) || name.isBlank()) {
            throw new IllegalArgumentException("Namespace sem nome. Informe o nome do namespace.");
        }

        description = isNull(description) ? "" : description;
    }
}
