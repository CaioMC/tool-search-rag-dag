package io.github.caiomc.toolsearch.adapters.agent.openai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.adapters.shared.openai.OpenAiHttpClient;
import io.github.caiomc.toolsearch.adapters.shared.openai.OpenAiSettings;
import io.github.caiomc.toolsearch.adapters.shared.openai.exception.OpenAiApiException;
import io.github.caiomc.toolsearch.core.agent.domain.enums.ToolExposure;
import io.github.caiomc.toolsearch.core.agent.port.llm.LlmSession;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.LlmSessionRequest;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.ModelResponse;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;

import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.billingCatalog;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sessão contra um servidor HTTP local que faz o papel da OpenAI/proxy.
 */
class OpenAiResponsesSessionTest {

    private final ObjectMapper json = new ObjectMapper();

    private final List<JsonNode> requests = new ArrayList<>();

    private final Deque<String> responses = new ArrayDeque<>();

    private HttpServer server;

    private OpenAiResponsesAdapter adapter;

    @BeforeEach
    void start() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.createContext("/v1/responses", exchange -> {
            try (InputStream body = exchange.getRequestBody(); OutputStream out = exchange.getResponseBody()) {
                this.requests.add(this.json.readTree(body));

                String response = this.responses.pop();
                int status = response.startsWith("{\"error\"") ? 400 : 200;
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);

                exchange.sendResponseHeaders(status, bytes.length);
                out.write(bytes);
            }
        });
        this.server.start();

        String baseUrl = "http://127.0.0.1:" + this.server.getAddress().getPort() + "/v1/";
        OpenAiSettings settings = new OpenAiSettings(
                baseUrl,
                "chave-de-teste",
                "gpt-teste",
                "emb-teste",
                Duration.ofSeconds(5)
        );

        this.adapter = new OpenAiResponsesAdapter(new OpenAiHttpClient(settings, this.json), this.json, settings.model());
    }

    @AfterEach
    void stop() {
        this.server.stop(0);
    }

    @Test
    void clientToolSearchRoundTripReplaysHistoryWithoutNullFieldsAndEchoesCallId() throws IOException {
        ToolCatalog catalog = billingCatalog();

        this.responses.add(this.resource("client-search-response.json"));
        this.responses.add(this.resource("final-response.json"));

        LlmSession session = this.adapter.startConversation(new LlmSessionRequest("instruções", "emita a NF 42", ToolExposure.CLIENT_TOOL_SEARCH, List.of(), catalog));

        session.sendToModel();
        session.addToolSearchOutput("call_xw9", List.of(catalog.require(ToolName.of("emitir_nota_fiscal"))));

        ModelResponse last = session.sendToModel();
        JsonNode second = this.requests.get(1);
        JsonNode replayedCall = second.path("input").get(1);
        JsonNode searchOutput = second.path("input").get(2);

        assertThat(last.text()).isEqualTo("Nota emitida.");
        assertThat(this.requests.get(0).path("model").asText()).isEqualTo("gpt-teste");
        assertThat(this.requests.get(0).path("tools").get(0).path("execution").asText()).isEqualTo("client");
        assertThat(replayedCall.path("type").asText()).isEqualTo("tool_search_call");
        assertThat(replayedCall.has("created_by")).as("campo nulo do proxy removido").isFalse();
        assertThat(searchOutput.path("type").asText()).isEqualTo("tool_search_output");
        assertThat(searchOutput.path("call_id").asText()).isEqualTo("call_xw9");
        assertThat(searchOutput.path("tools").get(0).path("defer_loading").asBoolean()).isTrue();
    }

    @Test
    void apiErrorBecomesExceptionWithStatusAndBody() {
        this.responses.add("{\"error\":{\"message\":\"array too long\"}}");

        LlmSession session = this.adapter.startConversation(new LlmSessionRequest("i", "p", ToolExposure.ALL_FUNCTIONS, List.of(), billingCatalog()));

        assertThatThrownBy(session::sendToModel)
                .isInstanceOf(OpenAiApiException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("array too long");
    }

    @Test
    void settingsNeverPrintTheKey() {
        OpenAiSettings settings = new OpenAiSettings("http://x", "s3gr3do", "m", "e", Duration.ofSeconds(1));

        assertThat(settings.toString()).doesNotContain("s3gr3do");
    }

    private String resource(String name) throws IOException {
        try (InputStream input = this.getClass().getResourceAsStream("/openai/" + name)) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
