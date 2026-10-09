package io.github.caiomc.toolsearch.core.catalog.domain.exception;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;

/**
 * Uma ferramenta declara dependência de outra que não existe no catálogo.
 */
public class UnknownDependencyException extends RuntimeException {

    public UnknownDependencyException(
            ToolName tool,
            ToolName dependency
    ) {
        super("A ferramenta '" + tool + "' depende de '" + dependency + "', que não existe no catálogo. Corrija o nome ou cadastre a dependência.");
    }
}
