package io.github.caiomc.toolsearch.adapters.agent.openai.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.caiomc.toolsearch.core.agent.domain.enums.ToolExposure;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.LlmSessionRequest;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolParameter;

/**
 * Traduz ferramentas do domínio para o formato do campo {@code tools} da Responses API,
 * conforme o modo de exposição. É aqui que aparecem {@code tool_search}, {@code defer_loading}
 * e {@code namespace}.
 */
public final class ResponsesToolMapper {

    /**
     * Função genérica do modo {@code SYSTEM_PROMPT}: o modelo informa o nome e os argumentos da
     * ferramenta que leu no prompt.
     */
    public static final String DISPATCHER = "executar_ferramenta";

    private final ObjectMapper json;

    public ResponsesToolMapper(ObjectMapper json) {
        this.json = json;
    }

    public String instructionsField(LlmSessionRequest request) {
        if (request.exposure() != ToolExposure.SYSTEM_PROMPT) {
            return request.instructions();
        }

        String catalog = request.tools().stream().map(this::describeAsText).collect(Collectors.joining("\n"));

        return request.instructions() + "\nPara usar uma ferramenta, chame a função " + DISPATCHER + " com o nome dela e os argumentos em JSON.\n\nFerramentas disponíveis:\n" + catalog;
    }

    public ArrayNode toolsField(LlmSessionRequest request) {
        ArrayNode tools = this.json.createArrayNode();

        switch (request.exposure()) {
            case SYSTEM_PROMPT -> tools.add(this.dispatcher());
            case ALL_FUNCTIONS, PRESELECTED_FUNCTIONS -> request.tools().forEach(tool -> tools.add(this.toFunction(tool, false)));
            case HOSTED_TOOL_SEARCH -> {
                tools.add(this.json.createObjectNode().put("type", "tool_search"));
                this.namespaces(request).forEach(tools::add);
            }
            case CLIENT_TOOL_SEARCH -> tools.add(this.clientToolSearch());
        }

        return tools;
    }

    /**
     * Ferramentas devolvidas num {@code tool_search_output} do modo cliente.
     */
    public ArrayNode toToolSearchOutputTools(List<ToolDefinition> tools) {
        ArrayNode loaded = this.json.createArrayNode();

        tools.forEach(tool -> loaded.add(this.toFunction(tool, true)));

        return loaded;
    }

    /**
     * Uma função no formato da Responses API. {@code strict} fica desligado porque o tool search
     * não aceita {@code strict: true} e porque há parâmetros opcionais.
     */
    public ObjectNode toFunction(
            ToolDefinition tool,
            boolean deferred
    ) {
        ObjectNode function = this.json.createObjectNode()
                .put("type", "function")
                .put("name", tool.name().value())
                .put("description", this.descriptionWithPrerequisites(tool))
                .put("strict", false);

        if (deferred) {
            function.put("defer_loading", true);
        }

        function.set("parameters", this.schema(tool.parameters()));

        return function;
    }

    /**
     * O DAG também informa o modelo: os pré-requisitos vão na descrição da função.
     */
    private String descriptionWithPrerequisites(ToolDefinition tool) {
        if (!tool.hasPrerequisites()) {
            return tool.description();
        }

        String prerequisites = tool.dependsOn().stream().map(ToolName::value).collect(Collectors.joining(", "));

        return tool.description() + " Pré-requisitos (executar antes): " + prerequisites + ".";
    }

    private String describeAsText(ToolDefinition tool) {
        String parameters = tool.parameters().stream()
                .map(parameter -> parameter.name() + ": " + parameter.type() + (parameter.required() ? "" : "?"))
                .collect(Collectors.joining(", "));

        return "- " + tool.name() + "(" + parameters + "): " + this.descriptionWithPrerequisites(tool);
    }

    private ObjectNode schema(List<ToolParameter> parameters) {
        ObjectNode schema = this.json.createObjectNode().put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        ArrayNode required = this.json.createArrayNode();

        for (ToolParameter parameter : parameters) {
            properties.putObject(parameter.name()).put("type", parameter.type()).put("description", parameter.description());

            if (parameter.required()) {
                required.add(parameter.name());
            }
        }

        schema.set("required", required);
        schema.put("additionalProperties", false);

        return schema;
    }

    /**
     * Modo hospedado: cada namespace leva suas funções com {@code defer_loading: true}. O modelo
     * vê nome e descrição do namespace e carrega as funções sob demanda.
     */
    private List<ObjectNode> namespaces(LlmSessionRequest request) {
        Map<String, List<ToolDefinition>> byNamespace = new LinkedHashMap<>();

        for (ToolDefinition tool : request.tools()) {
            byNamespace.computeIfAbsent(tool.namespace(), ignored -> new ArrayList<>()).add(tool);
        }

        return byNamespace.entrySet().stream()
                .map(entry -> this.namespace(request, entry.getKey(), entry.getValue()))
                .toList();
    }

    private ObjectNode namespace(
            LlmSessionRequest request,
            String name,
            List<ToolDefinition> tools
    ) {
        ObjectNode namespace = this.json.createObjectNode()
                .put("type", "namespace")
                .put("name", name)
                .put("description", request.catalog().namespace(name).description());

        ArrayNode functions = namespace.putArray("tools");

        tools.forEach(tool -> functions.add(this.toFunction(tool, true)));

        return namespace;
    }

    /**
     * Modo cliente: o modelo chama esta "ferramenta de busca" com um objetivo e a aplicação
     * responde com as funções encontradas.
     */
    private ObjectNode clientToolSearch() {
        ObjectNode search = this.json.createObjectNode()
                .put("type", "tool_search")
                .put("execution", "client")
                .put("description", "Busca no catálogo do ERP as ferramentas necessárias para a tarefa. Descreva em 'goal' o que precisa ser feito.");

        ObjectNode parameters = search.putObject("parameters").put("type", "object");

        parameters.putObject("properties").putObject("goal").put("type", "string").put("description", "Objetivo em linguagem natural, ex.: emitir a nota fiscal do pedido 42.");
        parameters.putArray("required").add("goal");
        parameters.put("additionalProperties", false);

        return search;
    }

    private ObjectNode dispatcher() {
        ObjectNode function = this.json.createObjectNode()
                .put("type", "function")
                .put("name", DISPATCHER)
                .put("description", "Executa uma das ferramentas descritas nas instruções.")
                .put("strict", false);

        ObjectNode parameters = function.putObject("parameters").put("type", "object");
        ObjectNode properties = parameters.putObject("properties");

        properties.putObject("ferramenta").put("type", "string").put("description", "Nome exato da ferramenta.");
        properties.putObject("argumentos").put("type", "string").put("description", "Argumentos da ferramenta em JSON.");
        parameters.putArray("required").add("ferramenta").add("argumentos");
        parameters.put("additionalProperties", false);

        return function;
    }
}
