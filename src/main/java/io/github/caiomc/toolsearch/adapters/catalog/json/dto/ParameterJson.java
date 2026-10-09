package io.github.caiomc.toolsearch.adapters.catalog.json.dto;

public record ParameterJson(
        String name,
        String type,
        String description,
        boolean required
) {
}
