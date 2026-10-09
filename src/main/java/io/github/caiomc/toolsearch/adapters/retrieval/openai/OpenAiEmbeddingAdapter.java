package io.github.caiomc.toolsearch.adapters.retrieval.openai;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.caiomc.toolsearch.adapters.shared.openai.OpenAiHttpClient;
import io.github.caiomc.toolsearch.core.retrieval.domain.model.Embedding;
import io.github.caiomc.toolsearch.core.retrieval.port.embedding.EmbeddingPort;

/**
 * {@link EmbeddingPort} sobre {@code POST /embeddings}, em lotes.
 */
public final class OpenAiEmbeddingAdapter implements EmbeddingPort {

    private static final int BATCH_SIZE = 100;

    private final OpenAiHttpClient http;

    private final ObjectMapper json;

    private final String model;

    public OpenAiEmbeddingAdapter(
            OpenAiHttpClient http,
            ObjectMapper json,
            String model
    ) {
        this.http = http;
        this.json = json;
        this.model = model;
    }

    @Override
    public List<Embedding> embed(List<String> texts) {
        List<Embedding> embeddings = new ArrayList<>();

        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            embeddings.addAll(this.embedBatch(texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()))));
        }

        return embeddings;
    }

    private List<Embedding> embedBatch(List<String> batch) {
        ObjectNode body = this.json.createObjectNode().put("model", this.model);
        ArrayNode input = body.putArray("input");

        batch.forEach(input::add);

        JsonNode response = this.http.post("/embeddings", body);
        Embedding[] ordered = new Embedding[batch.size()];

        for (JsonNode item : response.path("data")) {
            ordered[item.path("index").asInt()] = this.toEmbedding(item.path("embedding"));
        }

        return List.of(ordered);
    }

    private Embedding toEmbedding(JsonNode vector) {
        double[] values = new double[vector.size()];

        for (int i = 0; i < values.length; i++) {
            values[i] = vector.get(i).asDouble();
        }

        return new Embedding(values);
    }
}
