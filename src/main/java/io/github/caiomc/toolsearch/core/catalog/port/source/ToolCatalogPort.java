package io.github.caiomc.toolsearch.core.catalog.port.source;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;

/**
 * De onde vêm as ferramentas (arquivo JSON, banco, registro de MCPs...).
 */
public interface ToolCatalogPort {

    ToolCatalog load();
}
