package io.github.caiomc.toolsearch.core.retrieval.usecase;

import io.github.caiomc.toolsearch.core.retrieval.usecase.command.SelectToolsCommand;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.ToolSelection;

/**
 * Encontra as ferramentas relevantes para um pedido (RAG) e, opcionalmente, completa com os
 * pré-requisitos em ordem de execução (DAG). Não chama o modelo de linguagem.
 */
public interface SelectToolsUseCase {

    ToolSelection select(SelectToolsCommand command);
}
