package io.github.caiomc.toolsearch.adapters.cli.scenario.model;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;

/**
 * Cenário C1 de {@code docs/como-testar.md}: Embeddings entendem sinônimos. Precisa de OPENAI_API_KEY (no .env ou exportada).
 */
public final class ScenarioC1EmbeddingSynonyms {

    private ScenarioC1EmbeddingSynonyms() {
    }

    public static void main(String[] args) {
        Scenario.title(
                "C1. Embeddings entendem sinônimos",
                "emitir_nota_fiscal com a maior nota, mesmo com 'NF-e' e 'mandar'",
                "a primeira busca indexa as 250 ferramentas (uma chamada de embeddings)"
        );

        Scenario.command("buscar", "quero mandar a NF-e pra SEFAZ", "--estrategia", "embedding", "--top", "3");
    }
}
