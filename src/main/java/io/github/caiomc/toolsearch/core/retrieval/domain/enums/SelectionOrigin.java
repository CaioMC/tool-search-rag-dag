package io.github.caiomc.toolsearch.core.retrieval.domain.enums;

/**
 * Por que uma ferramenta entrou na seleção.
 */
public enum SelectionOrigin {

    /**
     * O RAG achou a ferramenta parecida com o pedido.
     */
    RAG,

    /**
     * O RAG não achou, mas o DAG trouxe porque uma ferramenta encontrada depende dela.
     */
    DAG
}
