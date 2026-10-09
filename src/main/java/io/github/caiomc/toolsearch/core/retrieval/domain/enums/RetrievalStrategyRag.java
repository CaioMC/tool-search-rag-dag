package io.github.caiomc.toolsearch.core.retrieval.domain.enums;

/**
 * Como o RAG encontra as ferramentas relevantes para um pedido.
 */
public enum RetrievalStrategyRag {

    /**
     * Busca lexical BM25: casa palavras. Roda local, sem rede, sem custo.
     */
    BM25,

    /**
     * Busca semântica por embeddings: casa significado ("NF" ~ "nota fiscal"). Uma chamada à API
     * para indexar o catálogo e uma por pergunta.
     */
    EMBEDDING
}
