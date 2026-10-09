package io.github.caiomc.toolsearch.adapters.agent.openai;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.caiomc.toolsearch.adapters.agent.openai.mapper.ResponsesOutputParser;
import io.github.caiomc.toolsearch.adapters.agent.openai.mapper.ResponsesToolMapper;
import io.github.caiomc.toolsearch.adapters.shared.openai.OpenAiHttpClient;
import io.github.caiomc.toolsearch.core.agent.port.llm.LlmSession;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.LlmSessionRequest;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.ModelResponse;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;

/**
 * Uma conversa na Responses API, sem estado no servidor: a cada {@link #sendToModel()} o histórico
 * inteiro ({@code input}) é reenviado, com os itens que o modelo produziu e as nossas respostas.
 *
 * <p>As ferramentas carregadas pelo tool search entram no fim do histórico (como
 * {@code tool_search_output}), então o começo do prompt continua igual e o cache de prompt
 * da OpenAI continua valendo.
 */
public final class OpenAiResponsesSession implements LlmSession {

    private final OpenAiHttpClient http;

    private final ResponsesToolMapper tools;

    private final ResponsesOutputParser parser;

    private final ObjectNode fixedRequest;

    private final ArrayNode history;

    public OpenAiResponsesSession(
            OpenAiHttpClient http,
            ResponsesToolMapper tools,
            ResponsesOutputParser parser,
            ObjectMapper json,
            String model,
            LlmSessionRequest request
    ) {
        this.http = http;
        this.tools = tools;
        this.parser = parser;
        this.history = json.createArrayNode();
        this.fixedRequest = json.createObjectNode()
                .put("model", model)
                .put("instructions", tools.instructionsField(request));

        this.fixedRequest.set("tools", tools.toolsField(request));
        this.history.addObject().put("role", "user").put("content", request.userPrompt());
    }

    @Override
    public ModelResponse sendToModel() {
        ObjectNode body = this.fixedRequest.deepCopy();

        body.set("input", this.history.deepCopy());

        JsonNode response = this.http.post("/responses", body);

        response.path("output").forEach(item -> this.history.add(withoutNulls(item)));

        return this.parser.toModelResponse(response);
    }

    @Override
    public void addToolSearchOutput(
            String callId,
            List<ToolDefinition> tools
    ) {
        ObjectNode output = this.history.addObject()
                .put("type", "tool_search_output")
                .put("call_id", callId)
                .put("execution", "client")
                .put("status", "completed");

        output.set("tools", this.tools.toToolSearchOutputTools(tools));
    }

    @Override
    public void addToolCallOutput(
            String callId,
            String output
    ) {
        this.history.addObject()
                .put("type", "function_call_output")
                .put("call_id", callId)
                .put("output", output);
    }

    /**
     * Proxies compatíveis (ex.: LiteLLM) acrescentam campos nulos como {@code created_by}, que a
     * OpenAI recusa quando o item é reenviado no {@code input}. Removemos todos os nulos.
     */
    private static JsonNode withoutNulls(JsonNode node) {
        JsonNode copy = node.deepCopy();

        removeNulls(copy);

        return copy;
    }

    private static void removeNulls(JsonNode node) {
        if (node instanceof ObjectNode object) {
            List<String> nullFields = new ArrayList<>();

            object.fields().forEachRemaining(field -> {
                if (field.getValue().isNull()) {
                    nullFields.add(field.getKey());
                } else {
                    removeNulls(field.getValue());
                }
            });

            object.remove(nullFields);
        }

        if (node instanceof ArrayNode array) {
            array.forEach(OpenAiResponsesSession::removeNulls);
        }
    }
}
