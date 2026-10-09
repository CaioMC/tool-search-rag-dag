package io.github.caiomc.toolsearch.core.agent.usecase.command;

import io.github.caiomc.toolsearch.core.agent.domain.enums.ToolExposure;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.RetrievalStrategyRag;

import static java.util.Objects.isNull;

/**
 * Pedido para rodar o agente.
 *
 * @param exposure    como as ferramentas chegam ao modelo
 * @param strategyRag RAG usado nos modos {@code PRESELECTED_FUNCTIONS} e {@code CLIENT_TOOL_SEARCH}
 * @param topKRag     quantas ferramentas o RAG devolve (antes do DAG)
 */
public record RunAgentCommand(
        String prompt,
        ToolExposure exposure,
        RetrievalStrategyRag strategyRag,
        int topKRag
) {

    public RunAgentCommand {
        if (isNull(prompt) || prompt.isBlank()) {
            throw new IllegalArgumentException("Pedido vazio. Escreva o que o agente deve fazer.");
        }

        if (isNull(exposure)) {
            throw new IllegalArgumentException("Modo de exposição das ferramentas não informado.");
        }

        if (topKRag < 1) {
            throw new IllegalArgumentException("topK deve ser maior que zero, mas foi " + topKRag + ".");
        }

        strategyRag = isNull(strategyRag) ? RetrievalStrategyRag.BM25 : strategyRag;
    }
}
