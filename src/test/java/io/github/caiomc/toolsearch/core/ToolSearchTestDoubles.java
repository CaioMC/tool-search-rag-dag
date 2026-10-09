package io.github.caiomc.toolsearch.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.caiomc.toolsearch.core.agent.domain.model.TokenUsage;
import io.github.caiomc.toolsearch.core.agent.port.execution.ToolExecutorPort;
import io.github.caiomc.toolsearch.core.agent.port.llm.LlmPort;
import io.github.caiomc.toolsearch.core.agent.port.llm.LlmSession;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.LlmSessionRequest;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.ModelAction;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.ModelResponse;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolNamespace;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolParameter;
import io.github.caiomc.toolsearch.core.retrieval.domain.model.Embedding;
import io.github.caiomc.toolsearch.core.retrieval.port.embedding.EmbeddingPort;

/**
 * Dublês escritos à mão para testar o core sem rede e sem framework.
 */
public final class ToolSearchTestDoubles {

    private ToolSearchTestDoubles() {
    }

    public static ToolDefinition tool(
            String name,
            String description,
            String... dependsOn
    ) {
        return new ToolDefinition(
                ToolName.of(name),
                "teste",
                "teste",
                description,
                List.of(new ToolParameter("codigo", "string", "Código.", true)),
                Arrays.stream(dependsOn).map(ToolName::of).collect(Collectors.toSet())
        );
    }

    /**
     * Mini catálogo de faturamento: emitir_nota_fiscal → calcular_impostos → determinar_cfop.
     */
    public static ToolCatalog billingCatalog() {
        return new ToolCatalog(
                List.of(new ToolNamespace("teste", "Ferramentas de teste.")),
                List.of(
                        tool("consultar_pedido_venda", "Consulta um pedido de venda pelo código."),
                        tool("determinar_cfop_operacao", "Determina o CFOP da operação fiscal."),
                        tool("calcular_impostos_operacao", "Calcula ICMS, IPI, PIS e COFINS da operação.", "determinar_cfop_operacao"),
                        tool("emitir_nota_fiscal", "Emite a nota fiscal eletrônica NF-e na SEFAZ.", "consultar_pedido_venda", "calcular_impostos_operacao"),
                        tool("cadastrar_funcionario", "Cadastra um funcionário no RH.")
                )
        );
    }

    public static ToolName name(String value) {
        return ToolName.of(value);
    }

    /**
     * Modelo roteirizado: devolve as respostas na ordem e grava o que a aplicação respondeu.
     */
    public static final class ScriptedLlm implements LlmPort, LlmSession {

        private final Deque<ModelResponse> responses;

        private final List<String> toolOutputs = new ArrayList<>();

        private final List<List<ToolName>> searchAnswers = new ArrayList<>();

        private LlmSessionRequest request;

        public ScriptedLlm(ModelResponse... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        public static ModelResponse response(ModelAction... actions) {
            return new ModelResponse(List.of(actions), "", new TokenUsage(100, 10, 5));
        }

        public static ModelResponse finalResponse(String text) {
            return new ModelResponse(List.of(), text, new TokenUsage(50, 0, 20));
        }

        @Override
        public LlmSession startConversation(LlmSessionRequest request) {
            this.request = request;

            return this;
        }

        @Override
        public String modelName() {
            return "roteiro";
        }

        @Override
        public ModelResponse sendToModel() {
            return this.responses.pop();
        }

        @Override
        public void addToolSearchOutput(
                String callId,
                List<ToolDefinition> tools
        ) {
            this.searchAnswers.add(tools.stream().map(ToolDefinition::name).toList());
        }

        @Override
        public void addToolCallOutput(
                String callId,
                String output
        ) {
            this.toolOutputs.add(output);
        }

        public LlmSessionRequest request() {
            return this.request;
        }

        public List<String> toolOutputs() {
            return this.toolOutputs;
        }

        public List<List<ToolName>> searchAnswers() {
            return this.searchAnswers;
        }
    }

    /**
     * Executor que só registra quais ferramentas rodaram.
     */
    public static final class RecordingExecutor implements ToolExecutorPort {

        private final List<ToolName> executed = new ArrayList<>();

        @Override
        public String execute(
                ToolDefinition tool,
                String arguments
        ) {
            this.executed.add(tool.name());

            return "{\"ok\":true}";
        }

        public List<ToolName> executed() {
            return this.executed;
        }
    }

    /**
     * Embeddings de brinquedo: uma dimensão por conceito, com sinônimos. Mostra o que o RAG
     * semântico faz e o BM25 não: "NF" e "nota fiscal" caem no mesmo lugar.
     */
    public static final class ConceptEmbeddings implements EmbeddingPort {

        private static final Map<String, Set<String>> CONCEPTS = Map.of(
                "nota", Set.of("nota", "nf", "nf-e", "nfe", "fiscal"),
                "imposto", Set.of("imposto", "impostos", "icms", "ipi", "tributo"),
                "pedido", Set.of("pedido", "venda", "order"),
                "pessoa", Set.of("funcionario", "funcionário", "colaborador", "rh")
        );

        private static final List<String> DIMENSIONS = List.of("nota", "imposto", "pedido", "pessoa");

        private int calls;

        @Override
        public List<Embedding> embed(List<String> texts) {
            this.calls++;

            return texts.stream().map(this::vector).toList();
        }

        public int calls() {
            return this.calls;
        }

        private Embedding vector(String text) {
            List<String> words = Arrays.asList(text.toLowerCase(Locale.ROOT).split("[^a-z0-9á-ú-]+"));
            double[] values = new double[DIMENSIONS.size() + 1];

            for (int i = 0; i < DIMENSIONS.size(); i++) {
                Set<String> synonyms = CONCEPTS.get(DIMENSIONS.get(i));

                values[i] = words.stream().filter(synonyms::contains).count();
            }

            values[DIMENSIONS.size()] = 0.01;

            return new Embedding(values);
        }
    }
}
