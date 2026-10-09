package io.github.caiomc.toolsearch.core.retrieval.usecase.command;

import io.github.caiomc.toolsearch.core.retrieval.domain.enums.RetrievalStrategyRag;

import static java.util.Objects.isNull;

/**
 * Pedido de busca de ferramentas.
 *
 * @param strategyRag como o RAG busca (BM25 ou embeddings)
 * @param topKRag     quantas ferramentas o RAG devolve (antes do DAG)
 * @param useDag      se o DAG deve acrescentar os pré-requisitos e ordenar
 */
public record SelectToolsCommand(
        String query,
        RetrievalStrategyRag strategyRag,
        int topKRag,
        boolean useDag
) {

    public SelectToolsCommand {
        if (isNull(query) || query.isBlank()) {
            throw new IllegalArgumentException("Pergunta vazia. Descreva o que o usuário quer fazer.");
        }

        if (topKRag < 1) {
            throw new IllegalArgumentException("topK deve ser maior que zero, mas foi " + topKRag + ".");
        }

        strategyRag = isNull(strategyRag) ? RetrievalStrategyRag.BM25 : strategyRag;
    }
}
