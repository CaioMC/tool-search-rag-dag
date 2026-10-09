package io.github.caiomc.toolsearch.core.agent.application;

/**
 * Parâmetros do agente.
 *
 * @param maxModelCalls limite de idas ao modelo por execução (protege contra laço infinito)
 * @param instructions  prompt de sistema base
 */
public record AgentSettings(
        int maxModelCalls,
        String instructions
) {

    public static final String DEFAULT_INSTRUCTIONS = """
            Você é o assistente de um ERP. Use as ferramentas para cumprir o pedido do usuário.
            Regras:
            - Algumas ferramentas têm pré-requisitos: execute-os antes, na ordem indicada.
            - Se uma ferramenta responder "pre_requisito_pendente", execute o que falta e tente de novo.
            - Não invente dados: use o que as ferramentas devolverem.
            - Ao terminar, responda em português, em no máximo 3 frases, citando o que foi feito.
            """;

    public AgentSettings {
        if (maxModelCalls < 1) {
            throw new IllegalArgumentException("maxModelCalls deve ser maior que zero, mas foi " + maxModelCalls + ".");
        }
    }

    public static AgentSettings defaults() {
        return new AgentSettings(15, DEFAULT_INSTRUCTIONS);
    }
}
