package io.github.caiomc.toolsearch.core.catalog.domain.exception;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;

/**
 * Ferramenta pedida não existe no catálogo.
 */
public class UnknownToolException extends RuntimeException {

    public UnknownToolException(ToolName tool) {
        super("Ferramenta '" + tool + "' não existe no catálogo.");
    }
}
