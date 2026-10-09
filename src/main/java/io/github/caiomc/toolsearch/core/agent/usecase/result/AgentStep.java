package io.github.caiomc.toolsearch.core.agent.usecase.result;

import java.util.List;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.ToolSelection;

/**
 * Um passo observado durante a execução do agente. Serve para o relatório da POC.
 */
public sealed interface AgentStep {

    /**
     * A OpenAI buscou e carregou ferramentas sozinha (tool search hospedado).
     */
    record HostedSearch(
            List<String> paths,
            List<ToolName> loaded
    ) implements AgentStep {

        public HostedSearch {
            paths = List.copyOf(paths);
            loaded = List.copyOf(loaded);
        }
    }

    /**
     * O modelo pediu ferramentas e a nossa aplicação respondeu com RAG + DAG.
     */
    record ClientSearch(
            String goal,
            ToolSelection selection
    ) implements AgentStep {
    }

    /**
     * O modelo chamou uma ferramenta. {@code missingPrerequisites} não vazio quer dizer que o DAG
     * barrou a chamada porque faltava executar algo antes.
     */
    record ToolCall(
            ToolName tool,
            String arguments,
            String output,
            List<ToolName> missingPrerequisites
    ) implements AgentStep {

        public ToolCall {
            missingPrerequisites = List.copyOf(missingPrerequisites);
        }

        public boolean blocked() {
            return !this.missingPrerequisites.isEmpty();
        }
    }
}
