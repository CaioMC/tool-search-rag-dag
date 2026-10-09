package io.github.caiomc.toolsearch.core.agent.domain.exception;

/**
 * O agente passou do número máximo de idas ao modelo sem chegar a uma resposta final.
 */
public class AgentTurnLimitException extends RuntimeException {

    public AgentTurnLimitException(int maxTurns) {
        super("O agente não terminou em " + maxTurns + " chamadas ao modelo. Aumente o limite ou simplifique o pedido.");
    }
}
