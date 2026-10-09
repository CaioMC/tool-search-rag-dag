package io.github.caiomc.toolsearch.core.catalog.domain.model;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * Uma ferramenta do catálogo do ERP.
 *
 * <p>{@code dependsOn} são as arestas do DAG: ferramentas que precisam ter sido executadas
 * antes desta (ex.: {@code emitir_nota_fiscal} depende de {@code calcular_impostos_operacao}).
 */
public record ToolDefinition(
        ToolName name,
        String namespace,
        String domain,
        String description,
        List<ToolParameter> parameters,
        Set<ToolName> dependsOn
) {

    public ToolDefinition {
        if (isNull(name)) {
            throw new IllegalArgumentException("Ferramenta sem nome. Informe o nome da ferramenta.");
        }

        if (isNull(description) || description.isBlank()) {
            throw new IllegalArgumentException("Ferramenta '" + name + "' sem descrição. A descrição é o que o modelo e o RAG usam para encontrá-la.");
        }

        if (isNull(namespace) || namespace.isBlank()) {
            throw new IllegalArgumentException("Ferramenta '" + name + "' sem namespace. Informe o namespace.");
        }

        if (nonNullContains(dependsOn, name)) {
            throw new IllegalArgumentException("Ferramenta '" + name + "' depende de si mesma. Remova a auto-dependência.");
        }

        domain = isNull(domain) ? "" : domain;
        parameters = isNull(parameters) ? List.of() : List.copyOf(parameters);
        dependsOn = isNull(dependsOn) ? Collections.emptySortedSet() : Collections.unmodifiableSortedSet(new TreeSet<>(dependsOn));
    }

    /**
     * Texto usado na indexação do RAG: nome legível, namespace, descrição e parâmetros.
     */
    public String searchableText() {
        String parameterNames = this.parameters.stream().map(ToolParameter::name).collect(Collectors.joining(" "));

        return String.join(
                " ",
                this.name.value().replace('_', ' '),
                this.namespace.replace('_', ' '),
                this.description,
                parameterNames.replace('_', ' ')
        );
    }

    public boolean hasPrerequisites() {
        return !this.dependsOn.isEmpty();
    }

    private static boolean nonNullContains(Set<ToolName> dependsOn, ToolName name) {
        return nonNull(dependsOn) && dependsOn.contains(name);
    }
}
