package io.github.caiomc.toolsearch.adapters.agent.openai.mapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.core.agent.port.llm.dto.ModelAction;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.ModelResponse;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;

import static org.assertj.core.api.Assertions.assertThat;

class ResponsesOutputParserTest {

    private final ObjectMapper json = new ObjectMapper();

    private final ResponsesOutputParser parser = new ResponsesOutputParser(this.json);

    @Test
    void hostedSearchBecomesAnObservationFollowedByTheToolCall() throws IOException {
        ModelResponse response = this.parser.toModelResponse(this.load("hosted-response.json"));

        assertThat(response.actions()).hasSize(2);
        assertThat(response.actions().get(0)).isEqualTo(new ModelAction.HostedToolSearch(List.of("faturamento_nota_fiscal"), List.of(ToolName.of("emitir_nota_fiscal"))));
        assertThat(response.actions().get(1)).isEqualTo(new ModelAction.ToolCall("call_9", ToolName.of("emitir_nota_fiscal"), "{\"codigo\":\"42\"}"));
        assertThat(response.usage().cachedInputTokens()).isEqualTo(1024);
        assertThat(response.isFinal()).isFalse();
    }

    @Test
    void clientSearchCarriesTheCallIdAndGoal() throws IOException {
        ModelResponse response = this.parser.toModelResponse(this.load("client-search-response.json"));

        assertThat(response.actions()).containsExactly(new ModelAction.ToolSearchCall("call_xw9", "Encontrar ferramentas para emitir a nota fiscal do pedido 42"));
    }

    @Test
    void messageIsTheFinalAnswer() throws IOException {
        ModelResponse response = this.parser.toModelResponse(this.load("final-response.json"));

        assertThat(response.isFinal()).isTrue();
        assertThat(response.text()).isEqualTo("Nota emitida.");
    }

    @Test
    void dispatcherCallFromSystemPromptModeIsUnwrapped() throws IOException {
        JsonNode json = this.json.readTree("""
                {"output": [{"type": "function_call", "call_id": "c1", "name": "executar_ferramenta",
                  "arguments": "{\\"ferramenta\\":\\"consultar_cliente\\",\\"argumentos\\":\\"{\\\\\\"codigo\\\\\\":\\\\\\"7\\\\\\"}\\"}"}]}
                """);

        ModelResponse response = this.parser.toModelResponse(json);

        assertThat(response.actions()).containsExactly(new ModelAction.ToolCall("c1", ToolName.of("consultar_cliente"), "{\"codigo\":\"7\"}"));
    }

    private JsonNode load(String name) throws IOException {
        try (InputStream input = this.getClass().getResourceAsStream("/openai/" + name)) {
            return this.json.readTree(input);
        }
    }
}
