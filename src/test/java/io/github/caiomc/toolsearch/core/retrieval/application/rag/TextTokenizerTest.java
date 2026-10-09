package io.github.caiomc.toolsearch.core.retrieval.application.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextTokenizerTest {

    @Test
    void removesAccentsStopwordsAndPlurals() {
        assertThat(TextTokenizer.tokenize("Emita as notas fiscais do pedido")).containsExactly("emit", "nota", "fiscal", "pedid");
    }

    @Test
    void verbFormsShareTheSameRoot() {
        assertThat(TextTokenizer.tokenize("emitir emita emite")).containsOnly("emit");
        assertThat(TextTokenizer.tokenize("consultar consulta")).containsOnly("consult");
    }
}
