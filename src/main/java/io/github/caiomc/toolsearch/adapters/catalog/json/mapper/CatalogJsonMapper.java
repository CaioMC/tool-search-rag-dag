package io.github.caiomc.toolsearch.adapters.catalog.json.mapper;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.caiomc.toolsearch.adapters.catalog.json.dto.CatalogJson;
import io.github.caiomc.toolsearch.adapters.catalog.json.dto.NamespaceJson;
import io.github.caiomc.toolsearch.adapters.catalog.json.dto.ParameterJson;
import io.github.caiomc.toolsearch.adapters.catalog.json.dto.ToolJson;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolNamespace;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolParameter;

import static java.util.Objects.isNull;

/**
 * Converte o JSON do catálogo no domínio. Erros de formato viram mensagens do domínio.
 */
public final class CatalogJsonMapper {

    public ToolCatalog toCatalog(CatalogJson json) {
        if (isNull(json) || isNull(json.tools())) {
            throw new IllegalArgumentException("Catálogo sem o campo 'tools'. Confira o arquivo JSON.");
        }

        List<ToolNamespace> namespaces = isNull(json.namespaces()) ?
                List.of() :
                json.namespaces().stream().map(this::toNamespace).toList();

        return new ToolCatalog(
                namespaces,
                json.tools().stream().map(this::toTool).toList()
        );
    }

    private ToolNamespace toNamespace(NamespaceJson json) {
        return new ToolNamespace(json.name(), json.description());
    }

    private ToolDefinition toTool(ToolJson json) {
        List<ToolParameter> parameters = isNull(json.parameters()) ?
                List.of() :
                json.parameters().stream().map(this::toParameter).toList();

        Set<ToolName> dependsOn = isNull(json.dependsOn()) ?
                Set.of() :
                json.dependsOn().stream().map(ToolName::of).collect(Collectors.toSet());

        return new ToolDefinition(
                ToolName.of(json.name()),
                json.namespace(),
                json.domain(),
                json.description(),
                parameters,
                dependsOn
        );
    }

    private ToolParameter toParameter(ParameterJson json) {
        return new ToolParameter(
                json.name(),
                json.type(),
                json.description(),
                json.required()
        );
    }
}
