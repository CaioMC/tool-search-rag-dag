package io.github.caiomc.toolsearch.core.agent.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.RecordingExecutor;
import io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.ScriptedLlm;
import io.github.caiomc.toolsearch.core.agent.domain.enums.ToolExposure;
import io.github.caiomc.toolsearch.core.agent.domain.exception.AgentTurnLimitException;
import io.github.caiomc.toolsearch.core.agent.port.llm.dto.ModelAction;
import io.github.caiomc.toolsearch.core.agent.usecase.command.RunAgentCommand;
import io.github.caiomc.toolsearch.core.agent.usecase.result.AgentRunReport;
import io.github.caiomc.toolsearch.core.agent.usecase.result.AgentStep;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDependencyDag;
import io.github.caiomc.toolsearch.core.retrieval.application.SelectToolsService;
import io.github.caiomc.toolsearch.core.retrieval.application.dag.PrerequisiteExpanderDag;
import io.github.caiomc.toolsearch.core.retrieval.application.rag.Bm25RetrieverRag;
import io.github.caiomc.toolsearch.core.retrieval.application.rag.ToolSearchRag;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.RetrievalStrategyRag;

import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.ScriptedLlm.finalResponse;
import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.ScriptedLlm.response;
import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.billingCatalog;
import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.name;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RunAgentServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC);

    private final ToolCatalog catalog = billingCatalog();

    private final ToolDependencyDag dag = ToolDependencyDag.of(this.catalog);

    private final RecordingExecutor executor = new RecordingExecutor();

    @Test
    void clientToolSearchIsAnsweredWithRagPlusDag() {
        ScriptedLlm llm = new ScriptedLlm(
                response(new ModelAction.ToolSearchCall("call_1", "emitir nota fiscal sefaz")),
                finalResponse("pronto")
        );

        AgentRunReport report = this.service(llm, 15).run(this.command(ToolExposure.CLIENT_TOOL_SEARCH));

        assertThat(llm.request().tools()).as("modo client não manda nada de início").isEmpty();
        assertThat(llm.searchAnswers()).containsExactly(
                List.of(
                        name("consultar_pedido_venda"),
                        name("determinar_cfop_operacao"),
                        name("calcular_impostos_operacao"),
                        name("emitir_nota_fiscal")
                )
        );

        assertThat(report.finalAnswer()).isEqualTo("pronto");
        assertThat(report.modelCalls()).isEqualTo(2);
        assertThat(report.usage().inputTokens()).isEqualTo(150);
    }

    @Test
    void dagBlocksToolWhosePrerequisitesDidNotRun() {
        ScriptedLlm llm = new ScriptedLlm(
                response(new ModelAction.ToolCall("c1", name("calcular_impostos_operacao"), "{}")),
                response(new ModelAction.ToolCall("c2", name("determinar_cfop_operacao"), "{}")),
                response(new ModelAction.ToolCall("c3", name("calcular_impostos_operacao"), "{}")),
                finalResponse("impostos calculados")
        );

        AgentRunReport report = this.service(llm, 15).run(this.command(ToolExposure.ALL_FUNCTIONS));

        assertThat(llm.toolOutputs().get(0)).contains("pre_requisito_pendente").contains("determinar_cfop_operacao");
        assertThat(this.executor.executed()).containsExactly(name("determinar_cfop_operacao"), name("calcular_impostos_operacao"));
        assertThat(report.blockedCalls()).isEqualTo(1);
        assertThat(report.toolsSentUpfront()).isEqualTo(this.catalog.size());
    }

    @Test
    void prerequisitesCalledInOrderInTheSameTurnAreAccepted() {
        ScriptedLlm llm = new ScriptedLlm(
                response(
                        new ModelAction.ToolCall("c1", name("determinar_cfop_operacao"), "{}"),
                        new ModelAction.ToolCall("c2", name("calcular_impostos_operacao"), "{}")
                ),
                finalResponse("ok")
        );

        AgentRunReport report = this.service(llm, 15).run(this.command(ToolExposure.ALL_FUNCTIONS));

        assertThat(report.blockedCalls()).isZero();
        assertThat(this.executor.executed()).hasSize(2);
    }

    @Test
    void unknownToolReturnsErrorToTheModelInsteadOfCrashing() {
        ScriptedLlm llm = new ScriptedLlm(
                response(new ModelAction.ToolCall("c1", name("ferramenta_fantasma"), "{}")),
                finalResponse("não deu")
        );

        this.service(llm, 15).run(this.command(ToolExposure.ALL_FUNCTIONS));

        assertThat(llm.toolOutputs()).singleElement().asString().contains("ferramenta_inexistente");
        assertThat(this.executor.executed()).isEmpty();
    }

    @Test
    void preselectionSendsOnlyRagPlusDagUpfront() {
        ScriptedLlm llm = new ScriptedLlm(finalResponse("ok"));

        AgentRunReport report = this.service(llm, 15).run(new RunAgentCommand("emitir nota fiscal sefaz", ToolExposure.PRESELECTED_FUNCTIONS, RetrievalStrategyRag.BM25, 1));

        assertThat(report.toolsSentUpfront()).isEqualTo(4);
        assertThat(report.steps()).first().isInstanceOf(AgentStep.ClientSearch.class);
    }

    @Test
    void stopsAfterTheTurnLimit() {
        ScriptedLlm llm = new ScriptedLlm(
                response(new ModelAction.ToolCall("c1", name("determinar_cfop_operacao"), "{}")),
                response(new ModelAction.ToolCall("c2", name("determinar_cfop_operacao"), "{}"))
        );

        assertThatThrownBy(() -> this.service(llm, 2).run(this.command(ToolExposure.ALL_FUNCTIONS)))
                .isInstanceOf(AgentTurnLimitException.class)
                .hasMessageContaining("2 chamadas");
    }

    private RunAgentService service(
            ScriptedLlm llm,
            int maxModelCalls
    ) {
        SelectToolsService selectTools = new SelectToolsService(
                new ToolSearchRag(List.of(new Bm25RetrieverRag(this.catalog))),
                new PrerequisiteExpanderDag(this.catalog, this.dag)
        );

        return new RunAgentService(
                llm,
                this.executor,
                selectTools,
                this.catalog,
                this.dag,
                new AgentSettings(maxModelCalls, "instruções"),
                CLOCK
        );
    }

    private RunAgentCommand command(ToolExposure exposure) {
        return new RunAgentCommand(
                "emitir nota fiscal",
                exposure,
                RetrievalStrategyRag.BM25,
                1
        );
    }
}
