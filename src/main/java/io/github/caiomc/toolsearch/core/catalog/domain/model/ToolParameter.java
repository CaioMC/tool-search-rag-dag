package io.github.caiomc.toolsearch.core.catalog.domain.model;

import java.util.Set;

import static java.util.Objects.isNull;

/**
 * Parâmetro de uma ferramenta. Vira uma propriedade do JSON Schema enviado ao modelo.
 */
public record ToolParameter(
        String name,
        String type,
        String description,
        boolean required
) {

    private static final Set<String> TYPES = Set.of("string", "integer", "number", "boolean");

    public ToolParameter {
        if (isNull(name) || name.isBlank()) {
            throw new IllegalArgumentException("Parâmetro sem nome. Informe o nome do parâmetro.");
        }

        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("Tipo '" + type + "' do parâmetro '" + name + "' não suportado. Use um de " + TYPES + ".");
        }

        description = isNull(description) ? "" : description;
    }
}
