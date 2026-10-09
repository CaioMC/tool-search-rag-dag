package io.github.caiomc.toolsearch.core.catalog.domain.model;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.caiomc.toolsearch.core.catalog.domain.exception.UnknownDependencyException;
import io.github.caiomc.toolsearch.core.catalog.domain.exception.UnknownToolException;

import static java.util.Objects.isNull;

/**
 * Todas as ferramentas conhecidas. Garante nomes únicos e que toda dependência aponta para uma
 * ferramenta que existe. A ausência de ciclos é garantida por {@link ToolDependencyDag}.
 */
public final class ToolCatalog {

    private final Map<ToolName, ToolDefinition> tools;

    private final Map<String, ToolNamespace> namespaces;

    public ToolCatalog(
            Collection<ToolNamespace> namespaces,
            Collection<ToolDefinition> tools
    ) {
        this.namespaces = new LinkedHashMap<>();
        this.tools = new LinkedHashMap<>();

        for (ToolNamespace namespace : isNull(namespaces) ? List.<ToolNamespace>of() : namespaces) {
            this.namespaces.put(namespace.name(), namespace);
        }

        for (ToolDefinition tool : tools) {
            if (this.tools.containsKey(tool.name())) {
                throw new IllegalArgumentException("Ferramenta duplicada no catálogo: '" + tool.name() + "'. Cada nome deve ser único.");
            }

            this.tools.put(tool.name(), tool);
            this.namespaces.putIfAbsent(tool.namespace(), new ToolNamespace(tool.namespace(), ""));
        }

        this.requireKnownDependencies();
    }

    public Optional<ToolDefinition> find(ToolName name) {
        return Optional.ofNullable(this.tools.get(name));
    }

    public ToolDefinition require(ToolName name) {
        return this.find(name).orElseThrow(() -> new UnknownToolException(name));
    }

    public List<ToolDefinition> all() {
        return List.copyOf(this.tools.values());
    }

    public List<ToolNamespace> namespaces() {
        return List.copyOf(this.namespaces.values());
    }

    public ToolNamespace namespace(String name) {
        return this.namespaces.getOrDefault(name, new ToolNamespace(name, ""));
    }

    public int size() {
        return this.tools.size();
    }

    private void requireKnownDependencies() {
        for (ToolDefinition tool : this.tools.values()) {
            for (ToolName dependency : tool.dependsOn()) {
                if (!this.tools.containsKey(dependency)) {
                    throw new UnknownDependencyException(tool.name(), dependency);
                }
            }
        }
    }
}
