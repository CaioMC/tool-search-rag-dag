package io.github.caiomc.toolsearch.core.retrieval.domain.model;

import static java.util.Objects.isNull;

/**
 * Vetor que representa o significado de um texto. Textos parecidos têm vetores próximos.
 */
public record Embedding(double[] values) {

    public Embedding {
        if (isNull(values) || values.length == 0) {
            throw new IllegalArgumentException("Embedding vazio. O provedor precisa devolver ao menos uma dimensão.");
        }

        values = values.clone();
    }

    /**
     * Similaridade de cosseno: 1 = mesma direção (mesmo sentido), 0 = sem relação.
     */
    public double cosineSimilarity(Embedding other) {
        if (other.values.length != this.values.length) {
            throw new IllegalArgumentException("Embeddings com dimensões diferentes: " + this.values.length + " e " + other.values.length + ". Use o mesmo modelo para indexar e consultar.");
        }

        double dot = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < this.values.length; i++) {
            dot += this.values[i] * other.values[i];
            normA += this.values[i] * this.values[i];
            normB += other.values[i] * other.values[i];
        }

        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
