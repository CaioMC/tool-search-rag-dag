package io.github.caiomc.toolsearch.core.agent.port.llm.dto;

import java.util.List;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;

/**
 * O que o modelo fez ou pediu em uma resposta.
 */
public sealed interface ModelAction {

    /**
     * Tool search hospedado: a OpenAI já buscou e carregou. Nada a responder; só registramos.
     */
    record HostedToolSearch(
            List<String> paths,
            List<ToolName> loaded
    ) implements ModelAction {

        public HostedToolSearch {
            paths = List.copyOf(paths);
            loaded = List.copyOf(loaded);
        }
    }

    /**
     * Tool search no cliente: o modelo descreveu o objetivo e espera que a gente devolva as
     * ferramentas com o mesmo {@code callId}.
     */
    record ToolSearchCall(
            String callId,
            String goal
    ) implements ModelAction {
    }

    /**
     * Chamada de função: executar {@code tool} com {@code arguments} (JSON) e devolver o resultado.
     */
    record ToolCall(
            String callId,
            ToolName tool,
            String arguments
    ) implements ModelAction {
    }
}
