package io.github.caiomc.toolsearch.adapters.shared.openai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.caiomc.toolsearch.adapters.shared.openai.exception.OpenAiApiException;

/**
 * POST de JSON na API da OpenAI com o {@link HttpClient} do JDK. Sem SDK de propósito: assim dá
 * para ver exatamente o que vai e o que volta.
 */
public final class OpenAiHttpClient {

    private final OpenAiSettings settings;

    private final ObjectMapper json;

    private final HttpClient http;

    public OpenAiHttpClient(
            OpenAiSettings settings,
            ObjectMapper json
    ) {
        this.settings = settings;
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(settings.timeout()).build();
    }

    public JsonNode post(
            String path,
            JsonNode body
    ) {
        HttpRequest request = HttpRequest
                .newBuilder(URI.create(this.settings.baseUrl() + path))
                .timeout(this.settings.timeout())
                .header("Authorization", "Bearer " + this.settings.apiKey())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(this.write(body)))
                .build();

        try {
            HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                throw new OpenAiApiException(response.statusCode(), path, response.body());
            }

            return this.json.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException("Falha de comunicação com " + this.settings.baseUrl() + path + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Chamada à OpenAI interrompida.", e);
        }
    }

    private String write(JsonNode body) {
        try {
            return this.json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar a requisição: " + e.getMessage(), e);
        }
    }
}
