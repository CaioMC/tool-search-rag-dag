package io.github.caiomc.toolsearch.adapters.agent.openai.mapper;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.caiomc.toolsearch.core.agent.domain.model.TokenUsage;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.ModelAction;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.ModelResponse;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;

/**
 * Lê o {@code output} de uma resposta da Responses API e devolve o que o modelo pediu.
 *
 * <p>Itens tratados:
 * <ul>
 *     <li>{@code tool_search_call} com {@code execution: server} + {@code tool_search_output}:
 *     busca feita pela OpenAI → {@link ModelAction.HostedToolSearch};</li>
 *     <li>{@code tool_search_call} com {@code execution: client}: precisamos responder →
 *     {@link ModelAction.ToolSearchCall};</li>
 *     <li>{@code function_call} → {@link ModelAction.ToolCall};</li>
 *     <li>{@code message} → texto final.</li>
 * </ul>
 */
public final class ResponsesOutputParser {

    private final ObjectMapper json;

    public ResponsesOutputParser(ObjectMapper json) {
        this.json = json;
    }

    public ModelResponse toModelResponse(JsonNode response) {
        List<ModelAction> actions = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        List<String> hostedPaths = new ArrayList<>();

        for (JsonNode item : response.path("output")) {
            String type = item.path("type").asText();

            switch (type) {
                case "tool_search_call" -> {
                    if ("client".equals(item.path("execution").asText())) {
                        actions.add(new ModelAction.ToolSearchCall(item.path("call_id").asText(), this.goal(item.path("arguments"))));
                    } else {
                        item.path("arguments").path("paths").forEach(path -> hostedPaths.add(path.asText()));
                    }
                }
                case "tool_search_output" -> {
                    if (!"client".equals(item.path("execution").asText())) {
                        actions.add(new ModelAction.HostedToolSearch(List.copyOf(hostedPaths), this.loadedToolNames(item.path("tools"))));
                        hostedPaths.clear();
                    }
                }
                case "function_call" -> actions.add(this.toolCall(item));
                case "message" -> item.path("content").forEach(content -> text.append(content.path("text").asText("")));
                default -> {
                }
            }
        }

        return new ModelResponse(
                actions,
                text.toString().trim(),
                this.usage(response.path("usage"))
        );
    }

    /**
     * No modo {@code SYSTEM_PROMPT} o modelo chama {@code executar_ferramenta}; aqui desembrulhamos
     * para o core ver a ferramenta real.
     */
    private ModelAction.ToolCall toolCall(JsonNode item) {
        String callId = item.path("call_id").asText();
        String name = item.path("name").asText();
        String arguments = item.path("arguments").asText("{}");

        if (!ResponsesToolMapper.DISPATCHER.equals(name)) {
            return new ModelAction.ToolCall(callId, ToolName.of(name), arguments);
        }

        JsonNode dispatched = this.parseJson(arguments);

        return new ModelAction.ToolCall(
                callId,
                ToolName.of(dispatched.path("ferramenta").asText()),
                dispatched.path("argumentos").asText("{}")
        );
    }

    private String goal(JsonNode arguments) {
        if (arguments.isTextual()) {
            return this.parseJson(arguments.asText()).path("goal").asText();
        }

        return arguments.path("goal").asText();
    }

    private List<ToolName> loadedToolNames(JsonNode tools) {
        List<ToolName> loaded = new ArrayList<>();

        for (JsonNode tool : tools) {
            if ("namespace".equals(tool.path("type").asText())) {
                loaded.addAll(this.loadedToolNames(tool.path("tools")));
            } else {
                loaded.add(ToolName.of(tool.path("name").asText()));
            }
        }

        return loaded;
    }

    private TokenUsage usage(JsonNode usage) {
        return new TokenUsage(
                usage.path("input_tokens").asLong(),
                usage.path("input_tokens_details").path("cached_tokens").asLong(),
                usage.path("output_tokens").asLong()
        );
    }

    private JsonNode parseJson(String value) {
        try {
            return this.json.readTree(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("O modelo devolveu argumentos que não são JSON: " + value, e);
        }
    }
}
