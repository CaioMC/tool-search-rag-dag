package io.github.caiomc.toolsearch.adapters.catalog.json.dto;

import java.util.List;

public record ToolJson(
        String name,
        String namespace,
        String domain,
        String description,
        List<ParameterJson> parameters,
        List<String> dependsOn
) {
}
