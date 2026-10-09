package io.github.caiomc.toolsearch.core.agent.domain.enums;

/**
 * Como as ferramentas chegam ao modelo. Cada modo é uma estratégia que a POC compara.
 */
public enum ToolExposure {

    /**
     * A abordagem ingênua: as 250 ferramentas descritas em texto no prompt de sistema e uma
     * única função genérica {@code executar_ferramenta}.
     */
    SYSTEM_PROMPT("prompt", "Todas no prompt de sistema (abordagem ingênua)"),

    /**
     * Todas como funções no campo {@code tools}. Na Chat Completions o limite é 128.
     */
    ALL_FUNCTIONS("todas", "Todas como funções em tools"),

    /**
     * RAG + DAG antes de chamar o modelo: só as top-k e seus pré-requisitos viram funções.
     * Funciona com qualquer modelo, inclusive os sem tool search.
     */
    PRESELECTED_FUNCTIONS("preselecao", "RAG + DAG antes da chamada (top-k)"),

    /**
     * Tool search hospedado: todas declaradas com {@code defer_loading}, em namespaces, e a
     * OpenAI decide quais carregar.
     */
    HOSTED_TOOL_SEARCH("hosted", "Tool search hospedado (OpenAI busca)"),

    /**
     * Tool search executado pelo cliente: o modelo pede ferramentas descrevendo o objetivo e a
     * nossa aplicação responde com RAG + DAG.
     */
    CLIENT_TOOL_SEARCH("client", "Tool search no cliente (nosso RAG + DAG)");

    private final String alias;

    private final String label;

    ToolExposure(
            String alias,
            String label
    ) {
        this.alias = alias;
        this.label = label;
    }

    public String alias() {
        return this.alias;
    }

    public String label() {
        return this.label;
    }

    public static ToolExposure fromAlias(String alias) {
        for (ToolExposure exposure : values()) {
            if (exposure.alias.equalsIgnoreCase(alias) || exposure.name().equalsIgnoreCase(alias)) {
                return exposure;
            }
        }

        throw new IllegalArgumentException("Modo desconhecido: '" + alias + "'. Use prompt, todas, preselecao, hosted ou client.");
    }
}
