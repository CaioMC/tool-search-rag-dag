package io.github.caiomc.toolsearch.adapters.agent.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.caiomc.toolsearch.core.agent.port.execution.ToolExecutorPort;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;

/**
 * ERP de mentira: devolve dados fixos e plausíveis para qualquer ferramenta, pelo prefixo do
 * nome (consultar_, pesquisar_, incluir_, alterar_ ou ação especial). Determinístico, para os
 * modos poderem ser comparados. Num sistema real, este adaptador chamaria as APIs do ERP.
 */
public final class SimulatedErpToolExecutorAdapter implements ToolExecutorPort {

    private final ObjectMapper json;

    public SimulatedErpToolExecutorAdapter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String execute(
            ToolDefinition tool,
            String arguments
    ) {
        String name = tool.name().value();
        JsonNode received = this.read(arguments);
        String code = received.path("codigo").asText("42");

        ObjectNode result = this.json.createObjectNode()
                .put("ferramenta", name)
                .put("status", "sucesso");

        result.set("argumentos_recebidos", received);

        if (name.startsWith("consultar_")) {
            result.putObject("dados").put("codigo", code).put("situacao", "ativo").put("descricao", "Registro " + code + " de " + tool.namespace());
        } else if (name.startsWith("pesquisar_")) {
            result.putArray("registros").add("REG-001").add("REG-002").add("REG-003");
        } else if (name.startsWith("incluir_")) {
            result.put("codigo_gerado", "NOVO-" + Math.abs(arguments.hashCode() % 1000));
        } else if (name.startsWith("alterar_")) {
            result.put("alterado", true).put("codigo", code);
        } else {
            result.put("protocolo", "PRT-" + Math.abs((name + code).hashCode() % 100000)).put("mensagem", "Executado: " + tool.description());
        }

        return result.toString();
    }

    private JsonNode read(String arguments) {
        try {
            return this.json.readTree(arguments);
        } catch (JsonProcessingException e) {
            return this.json.createObjectNode().put("bruto", arguments);
        }
    }
}
