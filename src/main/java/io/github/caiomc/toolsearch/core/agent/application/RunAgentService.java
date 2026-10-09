package io.github.caiomc.toolsearch.core.agent.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.caiomc.toolsearch.core.agent.domain.exception.AgentTurnLimitException;
import io.github.caiomc.toolsearch.core.agent.domain.model.TokenUsage;
import io.github.caiomc.toolsearch.core.agent.port.execution.ToolExecutorPort;
import io.github.caiomc.toolsearch.core.agent.port.llm.LlmPort;
import io.github.caiomc.toolsearch.core.agent.port.llm.LlmSession;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.LlmSessionRequest;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.ModelAction;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.ModelResponse;
import io.github.caiomc.toolsearch.core.agent.usecase.RunAgentUseCase;
import io.github.caiomc.toolsearch.core.agent.usecase.command.RunAgentCommand;
import io.github.caiomc.toolsearch.core.agent.usecase.result.AgentRunReport;
import io.github.caiomc.toolsearch.core.agent.usecase.result.AgentStep;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDependencyDag;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.retrieval.usecase.SelectToolsUseCase;
import io.github.caiomc.toolsearch.core.retrieval.usecase.command.SelectToolsCommand;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.ToolSelection;

/**
 * O laço do agente, igual para todos os modos de exposição:
 * <pre>
 * enquanto o modelo pedir algo:
 *     tool_search_call  → RAG + DAG escolhem as ferramentas → tool_search_output
 *     function_call     → DAG confere pré-requisitos → executa (ou recusa) → function_call_output
 * </pre>
 * O que muda entre os modos é só quais ferramentas vão na primeira chamada.
 */
public final class RunAgentService implements RunAgentUseCase {

    private final LlmPort llm;

    private final ToolExecutorPort executor;

    private final SelectToolsUseCase selectTools;

    private final ToolCatalog catalog;

    private final ToolDependencyDag dag;

    private final AgentSettings settings;

    private final Clock clock;

    public RunAgentService(
            LlmPort llm,
            ToolExecutorPort executor,
            SelectToolsUseCase selectTools,
            ToolCatalog catalog,
            ToolDependencyDag dag,
            AgentSettings settings,
            Clock clock
    ) {
        this.llm = llm;
        this.executor = executor;
        this.selectTools = selectTools;
        this.catalog = catalog;
        this.dag = dag;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public AgentRunReport run(RunAgentCommand command) {
        Instant start = this.clock.instant();
        List<AgentStep> steps = new ArrayList<>();
        List<ToolDefinition> firstRequestTools = this.toolsForFirstRequest(command, steps);

        LlmSession session = this.llm.startConversation(
                new LlmSessionRequest(
                        this.settings.instructions(),
                        command.prompt(),
                        command.exposure(),
                        firstRequestTools,
                        this.catalog
                )
        );

        Set<ToolName> executed = new HashSet<>();
        TokenUsage usage = TokenUsage.ZERO;

        for (int call = 1; call <= this.settings.maxModelCalls(); call++) {
            ModelResponse response = session.sendToModel();

            usage = usage.plus(response.usage());

            for (ModelAction action : response.actions()) {
                steps.add(
                        this.respondTo(
                                action,
                                session,
                                command,
                                executed
                        )
                );
            }

            if (response.isFinal()) {
                return new AgentRunReport(
                        command.prompt(),
                        command.exposure(),
                        this.llm.modelName(),
                        firstRequestTools.size(),
                        response.text(),
                        steps,
                        call,
                        usage,
                        Duration.between(start, this.clock.instant())
                );
            }
        }

        throw new AgentTurnLimitException(this.settings.maxModelCalls());
    }

    /**
     * A única diferença real entre os modos: quais ferramentas vão na primeira requisição, antes
     * de o modelo pedir algo.
     */
    private List<ToolDefinition> toolsForFirstRequest(
            RunAgentCommand command,
            List<AgentStep> steps
    ) {
        switch (command.exposure()) {
            case PRESELECTED_FUNCTIONS -> {
                ToolSelection selection = this.selectToolsRagDag(command, command.prompt());

                steps.add(new AgentStep.ClientSearch(command.prompt(), selection));

                return selection.definitions();
            }
            case CLIENT_TOOL_SEARCH -> {
                return List.of();
            }
            default -> {
                return this.catalog.all();
            }
        }
    }

    private AgentStep respondTo(
            ModelAction action,
            LlmSession session,
            RunAgentCommand command,
            Set<ToolName> executed
    ) {
        if (action instanceof ModelAction.ToolSearchCall search) {
            ToolSelection selection = this.selectToolsRagDag(command, search.goal());

            session.addToolSearchOutput(search.callId(), selection.definitions());

            return new AgentStep.ClientSearch(search.goal(), selection);
        }

        if (action instanceof ModelAction.ToolCall call) {
            return this.executeIfPrerequisitesMet(call, session, executed);
        }

        ModelAction.HostedToolSearch hosted = (ModelAction.HostedToolSearch) action;

        return new AgentStep.HostedSearch(hosted.paths(), hosted.loaded());
    }

    /**
     * Aqui o DAG vira regra em tempo de execução: uma ferramenta só roda se os pré-requisitos
     * diretos já rodaram nesta conversa. Senão, o modelo recebe um erro dizendo o que falta.
     */
    private AgentStep executeIfPrerequisitesMet(
            ModelAction.ToolCall call,
            LlmSession session,
            Set<ToolName> executed
    ) {
        Optional<ToolDefinition> tool = this.catalog.find(call.tool());

        if (tool.isEmpty()) {
            String output = "{\"erro\":\"ferramenta_inexistente\",\"mensagem\":\"A ferramenta " + call.tool() + " não existe.\"}";

            session.addToolCallOutput(call.callId(), output);

            return this.step(call, output, List.of());
        }

        Set<ToolName> missingPrerequisitesDag = this.dag.missingPrerequisites(call.tool(), executed);

        if (!missingPrerequisitesDag.isEmpty()) {
            String output = blockedByDagOutput(call.tool(), missingPrerequisitesDag);

            session.addToolCallOutput(call.callId(), output);

            return this.step(call, output, List.copyOf(missingPrerequisitesDag));
        }

        String output = this.executor.execute(tool.get(), call.arguments());

        executed.add(call.tool());
        session.addToolCallOutput(call.callId(), output);

        return this.step(call, output, List.of());
    }

    private AgentStep step(
            ModelAction.ToolCall call,
            String output,
            List<ToolName> missingPrerequisites
    ) {
        return new AgentStep.ToolCall(
                call.tool(),
                call.arguments(),
                output,
                missingPrerequisites
        );
    }

    private ToolSelection selectToolsRagDag(
            RunAgentCommand command,
            String query
    ) {
        return this.selectTools.select(
                new SelectToolsCommand(
                        query,
                        command.strategyRag(),
                        command.topKRag(),
                        true
                )
        );
    }

    private static String blockedByDagOutput(
            ToolName tool,
            Set<ToolName> missing
    ) {
        String names = missing.stream().map(name -> "\"" + name + "\"").collect(Collectors.joining(","));

        return "{\"erro\":\"pre_requisito_pendente\",\"ferramenta\":\"" + tool + "\",\"executar_antes\":[" + names + "],\"mensagem\":\"Execute antes as ferramentas listadas em executar_antes e depois chame " + tool + " de novo.\"}";
    }
}
