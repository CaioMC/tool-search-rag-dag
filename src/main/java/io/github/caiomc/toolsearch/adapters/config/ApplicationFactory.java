package io.github.caiomc.toolsearch.adapters.config;

import java.io.PrintStream;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.caiomc.toolsearch.adapters.agent.execution.SimulatedErpToolExecutorAdapter;
import io.github.caiomc.toolsearch.adapters.agent.openai.OpenAiResponsesAdapter;
import io.github.caiomc.toolsearch.adapters.catalog.json.JsonToolCatalogAdapter;
import io.github.caiomc.toolsearch.adapters.cli.controller.CliController;
import io.github.caiomc.toolsearch.adapters.cli.mapper.CliCommandMapper;
import io.github.caiomc.toolsearch.adapters.cli.presenter.ReportPrinter;
import io.github.caiomc.toolsearch.adapters.retrieval.openai.OpenAiEmbeddingAdapter;
import io.github.caiomc.toolsearch.adapters.shared.openai.OpenAiHttpClient;
import io.github.caiomc.toolsearch.adapters.shared.openai.OpenAiSettings;
import io.github.caiomc.toolsearch.core.agent.application.AgentSettings;
import io.github.caiomc.toolsearch.core.agent.application.RunAgentService;
import io.github.caiomc.toolsearch.core.agent.usecase.RunAgentUseCase;
import io.github.caiomc.toolsearch.core.catalog.application.DescribeCatalogService;
import io.github.caiomc.toolsearch.core.catalog.application.PlanExecutionService;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDependencyDag;
import io.github.caiomc.toolsearch.core.catalog.usecase.DescribeCatalogUseCase;
import io.github.caiomc.toolsearch.core.catalog.usecase.PlanExecutionUseCase;
import io.github.caiomc.toolsearch.core.retrieval.application.SelectToolsService;
import io.github.caiomc.toolsearch.core.retrieval.application.dag.PrerequisiteExpanderDag;
import io.github.caiomc.toolsearch.core.retrieval.application.rag.Bm25RetrieverRag;
import io.github.caiomc.toolsearch.core.retrieval.application.rag.EmbeddingRetrieverRag;
import io.github.caiomc.toolsearch.core.retrieval.application.rag.ToolSearchRag;
import io.github.caiomc.toolsearch.core.retrieval.port.embedding.EmbeddingPort;
import io.github.caiomc.toolsearch.core.retrieval.usecase.SelectToolsUseCase;

import static java.util.Objects.isNull;

/**
 * Composition root: o único lugar que conhece core e adapters ao mesmo tempo e liga um no outro.
 * Sem framework de injeção de dependência, para a arquitetura ficar visível.
 *
 * <p>O acesso à OpenAI é criado só quando um comando precisa dele, então {@code catalogo},
 * {@code plano} e {@code buscar --estrategia bm25} funcionam sem chave.
 */
public final class ApplicationFactory {

    private final Map<String, String> env;

    private final ObjectMapper json;

    private final ToolCatalog catalog;

    private final ToolDependencyDag dag;

    private OpenAiSettings openAiSettings;

    private OpenAiHttpClient http;

    private SelectToolsUseCase selectTools;

    public ApplicationFactory(Map<String, String> env) {
        this.env = env;
        this.json = new ObjectMapper();
        this.catalog = new JsonToolCatalogAdapter(env.getOrDefault("TOOLSEARCH_CATALOG", JsonToolCatalogAdapter.DEFAULT_LOCATION)).load();
        this.dag = ToolDependencyDag.of(this.catalog);
    }

    public CliController cliController(PrintStream out) {
        return new CliController(
                this.describeCatalog(),
                this.planExecution(),
                this.selectTools(),
                this::runAgent,
                new CliCommandMapper(),
                new ReportPrinter(out, this.modelName())
        );
    }

    public DescribeCatalogUseCase describeCatalog() {
        return new DescribeCatalogService(this.catalog, this.dag);
    }

    public PlanExecutionUseCase planExecution() {
        return new PlanExecutionService(this.catalog, this.dag);
    }

    /**
     * Uma instância só, para o índice de embeddings ser montado uma vez por execução.
     */
    public synchronized SelectToolsUseCase selectTools() {
        if (isNull(this.selectTools)) {
            EmbeddingPort lazyEmbeddings = texts -> this.embeddings().embed(texts);

            ToolSearchRag searchRag = new ToolSearchRag(
                    List.of(
                            new Bm25RetrieverRag(this.catalog),
                            new EmbeddingRetrieverRag(this.catalog, lazyEmbeddings)
                    )
            );

            this.selectTools = new SelectToolsService(
                    searchRag,
                    new PrerequisiteExpanderDag(this.catalog, this.dag)
            );
        }

        return this.selectTools;
    }

    public RunAgentUseCase runAgent() {
        return new RunAgentService(
                new OpenAiResponsesAdapter(this.http(), this.json, this.openAiSettings().model()),
                new SimulatedErpToolExecutorAdapter(this.json),
                this.selectTools(),
                this.catalog,
                this.dag,
                AgentSettings.defaults(),
                Clock.systemUTC()
        );
    }

    public String modelName() {
        return this.env.getOrDefault("OPENAI_MODEL", OpenAiSettings.DEFAULT_MODEL);
    }

    private EmbeddingPort embeddings() {
        return new OpenAiEmbeddingAdapter(
                this.http(),
                this.json,
                this.openAiSettings().embeddingModel()
        );
    }

    private synchronized OpenAiHttpClient http() {
        if (isNull(this.http)) {
            this.http = new OpenAiHttpClient(this.openAiSettings(), this.json);
        }

        return this.http;
    }

    private synchronized OpenAiSettings openAiSettings() {
        if (isNull(this.openAiSettings)) {
            this.openAiSettings = OpenAiSettings.fromEnvironment(this.env);
        }

        return this.openAiSettings;
    }
}
