package io.github.caiomc.toolsearch.core.agent.usecase;

import io.github.caiomc.toolsearch.core.agent.usecase.command.RunAgentCommand;
import io.github.caiomc.toolsearch.core.agent.usecase.result.AgentRunReport;

/**
 * Roda o agente de ponta a ponta com o modelo, no modo de exposição de ferramentas pedido.
 */
public interface RunAgentUseCase {

    AgentRunReport run(RunAgentCommand command);
}
