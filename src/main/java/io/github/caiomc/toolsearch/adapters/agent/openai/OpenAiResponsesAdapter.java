package io.github.caiomc.toolsearch.adapters.agent.openai;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.caiomc.toolsearch.adapters.agent.openai.mapper.ResponsesOutputParser;
import io.github.caiomc.toolsearch.adapters.agent.openai.mapper.ResponsesToolMapper;
import io.github.caiomc.toolsearch.adapters.shared.openai.OpenAiHttpClient;
import io.github.caiomc.toolsearch.core.agent.port.llm.LlmPort;
import io.github.caiomc.toolsearch.core.agent.port.llm.LlmSession;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.LlmSessionRequest;

/**
 * {@link LlmPort} sobre a Responses API ({@code POST /responses}).
 */
public final class OpenAiResponsesAdapter implements LlmPort {

    private final OpenAiHttpClient http;

    private final ObjectMapper json;

    private final String model;

    private final ResponsesToolMapper tools;

    private final ResponsesOutputParser parser;

    public OpenAiResponsesAdapter(
            OpenAiHttpClient http,
            ObjectMapper json,
            String model
    ) {
        this.http = http;
        this.json = json;
        this.model = model;
        this.tools = new ResponsesToolMapper(json);
        this.parser = new ResponsesOutputParser(json);
    }

    @Override
    public LlmSession startConversation(LlmSessionRequest request) {
        return new OpenAiResponsesSession(
                this.http,
                this.tools,
                this.parser,
                this.json,
                this.model,
                request
        );
    }

    @Override
    public String modelName() {
        return this.model;
    }
}
