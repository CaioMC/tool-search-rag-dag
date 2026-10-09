package io.github.caiomc.toolsearch.core.retrieval.port.embedding;

import java.util.List;

import io.github.caiomc.toolsearch.core.retrieval.domain.model.Embedding;

/**
 * Transforma textos em vetores (modelo de embeddings). Um vetor por texto, na mesma ordem.
 */
public interface EmbeddingPort {

    List<Embedding> embed(List<String> texts);
}
