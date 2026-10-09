package io.github.caiomc.toolsearch.adapters.cli.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import io.github.caiomc.toolsearch.adapters.cli.dto.CliArguments;
import io.github.caiomc.toolsearch.adapters.cli.dto.ComparisonRow;
import io.github.caiomc.toolsearch.adapters.cli.mapper.CliCommandMapper;
import io.github.caiomc.toolsearch.adapters.cli.presenter.ReportPrinter;
import io.github.caiomc.toolsearch.core.agent.domain.enums.ToolExposure;
import io.github.caiomc.toolsearch.core.agent.usecase.RunAgentUseCase;
import io.github.caiomc.toolsearch.core.agent.usecase.command.RunAgentCommand;
import io.github.caiomc.toolsearch.core.agent.usecase.result.AgentRunReport;
import io.github.caiomc.toolsearch.core.catalog.usecase.DescribeCatalogUseCase;
import io.github.caiomc.toolsearch.core.catalog.usecase.PlanExecutionUseCase;
import io.github.caiomc.toolsearch.core.retrieval.usecase.SelectToolsUseCase;

/**
 * Recebe o comando, traduz (mapper), delega ao caso de uso e manda imprimir (presenter).
 *
 * <p>{@code runAgent} é um {@link Supplier} porque o agente precisa da chave da OpenAI: assim os
 * comandos locais funcionam sem ela.
 */
public final class CliController {

    private final DescribeCatalogUseCase describeCatalog;

    private final PlanExecutionUseCase planExecution;

    private final SelectToolsUseCase selectTools;

    private final Supplier<RunAgentUseCase> runAgent;

    private final CliCommandMapper mapper;

    private final ReportPrinter printer;

    public CliController(
            DescribeCatalogUseCase describeCatalog,
            PlanExecutionUseCase planExecution,
            SelectToolsUseCase selectTools,
            Supplier<RunAgentUseCase> runAgent,
            CliCommandMapper mapper,
            ReportPrinter printer
    ) {
        this.describeCatalog = describeCatalog;
        this.planExecution = planExecution;
        this.selectTools = selectTools;
        this.runAgent = runAgent;
        this.mapper = mapper;
        this.printer = printer;
    }

    public void handle(CliArguments args) {
        switch (args.command()) {
            case "catalogo" -> this.printer.overview(this.describeCatalog.describe());
            case "plano" -> this.printer.plan(
                    this.planExecution.plan(
                            this.mapper.toToolName(args)
                    )
            );
            case "buscar" -> this.printer.selection(
                    this.selectTools.select(
                            this.mapper.toSelectCommand(args)
                    )
            );
            case "agente" -> this.printer.report(
                    this.run(
                            this.mapper.toRunAgentCommand(args, this.mapper.toExposure(args))
                    ),
                    true
            );
            case "comparar" -> this.printer.comparison(
                    this.compare(args),
                    this.mapper.detailed(args)
            );
            default -> this.printer.help();
        }
    }

    private List<ComparisonRow> compare(CliArguments args) {
        List<ComparisonRow> rows = new ArrayList<>();

        for (ToolExposure exposure : this.mapper.toExposures(args)) {
            this.printer.progress("Rodando modo " + exposure.alias() + "...");

            try {
                rows.add(new ComparisonRow(exposure, this.run(this.mapper.toRunAgentCommand(args, exposure)), null));
            } catch (RuntimeException e) {
                rows.add(new ComparisonRow(exposure, null, e.getMessage()));
            }
        }

        return rows;
    }

    /**
     * O comando já chega convertido: erros de argumento aparecem antes de exigir a chave da OpenAI.
     */
    private AgentRunReport run(RunAgentCommand command) {
        return this.runAgent.get().run(command);
    }
}
