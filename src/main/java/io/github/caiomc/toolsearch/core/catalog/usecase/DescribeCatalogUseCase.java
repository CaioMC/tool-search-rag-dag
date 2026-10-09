package io.github.caiomc.toolsearch.core.catalog.usecase;

import io.github.caiomc.toolsearch.core.catalog.usecase.result.CatalogOverview;

/**
 * Resume o catálogo: quantas ferramentas, namespaces e dependências.
 */
public interface DescribeCatalogUseCase {

    CatalogOverview describe();
}
