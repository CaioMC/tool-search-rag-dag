package io.github.caiomc.toolsearch.core.agent.port.execution;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;

/**
 * Executa de fato uma ferramenta (num sistema real: chamada ao ERP). Devolve o resultado como texto
 * (normalmente JSON), que volta para o modelo como {@code function_call_output}.
 */
public interface ToolExecutorPort {

    String execute(
            ToolDefinition tool,
            String arguments
    );
}
