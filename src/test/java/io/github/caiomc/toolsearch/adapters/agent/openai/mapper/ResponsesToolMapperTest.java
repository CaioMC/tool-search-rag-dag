package io.github.caiomc.toolsearch.adapters.agent.openai.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.core.agent.domain.enums.ToolExposure;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.LlmSessionRequest;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;

import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.billingCatalog;
import static org.assertj.core.api.Assertions.assertThat;

class ResponsesToolMapperTest {

    private final ResponsesToolMapper mapper = new ResponsesToolMapper(new ObjectMapper());

    private final ToolCatalog catalog = billingCatalog();

    @Test
    void hostedModeDeclaresToolSearchAndDeferredFunctionsInsideNamespaces() {
        ArrayNode tools = this.mapper.toolsField(this.request(ToolExposure.HOSTED_TOOL_SEARCH));

        assertThat(tools.get(0).path("type").asText()).isEqualTo("tool_search");
        assertThat(tools.get(1).path("type").asText()).isEqualTo("namespace");
        assertThat(tools.get(1).path("description").asText()).isEqualTo("Ferramentas de teste.");
        assertThat(tools.get(1).path("tools")).hasSize(this.catalog.size()).allSatisfy(function -> assertThat(function.path("defer_loading").asBoolean()).isTrue());
    }

    @Test
    void clientModeDeclaresOnlyTheClientExecutedSearch() {
        ArrayNode tools = this.mapper.toolsField(this.request(ToolExposure.CLIENT_TOOL_SEARCH));

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).path("execution").asText()).isEqualTo("client");
        assertThat(tools.get(0).path("parameters").path("required").get(0).asText()).isEqualTo("goal");
    }

    @Test
    void allFunctionsModeSendsPlainFunctionsWithPrerequisitesInTheDescription() {
        ArrayNode tools = this.mapper.toolsField(this.request(ToolExposure.ALL_FUNCTIONS));
        JsonNode emitir = tools.get(3);

        assertThat(tools).hasSize(this.catalog.size());
        assertThat(emitir.has("defer_loading")).isFalse();
        assertThat(emitir.path("description").asText()).contains("Pré-requisitos (executar antes): calcular_impostos_operacao, consultar_pedido_venda");
        assertThat(emitir.path("parameters").path("required").get(0).asText()).isEqualTo("codigo");
    }

    @Test
    void systemPromptModeListsToolsAsTextAndExposesOnlyTheDispatcher() {
        LlmSessionRequest request = this.request(ToolExposure.SYSTEM_PROMPT);

        assertThat(this.mapper.toolsField(request)).singleElement().satisfies(tool -> assertThat(tool.path("name").asText()).isEqualTo(ResponsesToolMapper.DISPATCHER));
        assertThat(this.mapper.instructionsField(request)).contains("- emitir_nota_fiscal(codigo: string)");
    }

    private LlmSessionRequest request(ToolExposure exposure) {
        return new LlmSessionRequest(
                "instruções",
                "pedido",
                exposure,
                this.catalog.all(),
                this.catalog
        );
    }
}
