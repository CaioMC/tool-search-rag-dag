package io.github.caiomc.toolsearch.adapters.cli.presenter;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import io.github.caiomc.toolsearch.adapters.cli.dto.ComparisonRow;
import io.github.caiomc.toolsearch.core.agent.usecase.result.AgentRunReport;
import io.github.caiomc.toolsearch.core.agent.usecase.result.AgentStep;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.catalog.usecase.result.CatalogOverview;
import io.github.caiomc.toolsearch.core.catalog.usecase.result.ExecutionPlan;
import io.github.caiomc.toolsearch.core.catalog.usecase.result.PlanStep;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.SelectionOrigin;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.SelectedTool;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.ToolSelection;

import static java.util.Objects.isNull;

/**
 * Formata os resultados para o terminal.
 */
public final class ReportPrinter {

    private static final int OUTPUT_PREVIEW = 110;

    private static final String HELP = """
                POC de tool search com RAG + DAG (modelo padrão: %s)

                Sem chave (local):
                  catalogo                                  números do catálogo e do DAG
                  plano <ferramenta>                        ordem de execução pelo DAG
                  buscar "<pedido>" [--top 5] [--sem-dag]   RAG BM25 (+ DAG)

                Com OPENAI_API_KEY:
                  buscar "<pedido>" --estrategia embedding  RAG semântico (+ DAG)
                  agente "<pedido>" --modo <modo>           roda o agente de ponta a ponta
                  comparar "<pedido>" [--modos a,b] [--detalhes]  roda todos os modos e compara

                Modos: prompt | todas | preselecao | hosted | client (padrão)
                Opções do RAG nos modos preselecao/client: --estrategia bm25|embedding  --top N
                """;

    private final PrintStream out;

    private final String model;

    public ReportPrinter(
            PrintStream out,
            String model
    ) {
        this.out = out;
        this.model = model;
    }

    public void help() {
        this.out.println(HELP.formatted(this.model));
    }

    public void progress(String message) {
        this.out.println(message);
    }

    public void overview(CatalogOverview overview) {
        this.out.println("Catálogo");
        this.out.printf("  ferramentas ..................... %d%n", overview.toolCount());
        this.out.printf("  namespaces ...................... %d%n", overview.namespaceCount());
        this.out.printf("  arestas no DAG (dependências) ... %d%n", overview.dependencyCount());
        this.out.printf("  ferramentas com pré-requisito ... %d%n", overview.toolsWithPrerequisites());
        this.out.printf("  maior cadeia .................... %d níveis, terminando em %s%n", overview.deepestChain(), overview.deepestTool());
        this.out.println();
        this.out.println("Veja a cadeia com: plano " + overview.deepestTool());
    }

    public void plan(ExecutionPlan plan) {
        this.out.println("Para executar " + plan.target() + ", o DAG exige esta ordem:");

        for (int i = 0; i < plan.steps().size(); i++) {
            PlanStep step = plan.steps().get(i);
            String prerequisites = step.prerequisites().stream().map(ToolName::value).collect(Collectors.joining(", "));

            this.out.printf(
                    "  %2d. %-34s %s%n",
                    i + 1,
                    step.tool(),
                    prerequisites.isEmpty() ? "" : "precisa de: " + prerequisites
            );
        }
    }

    public void selection(ToolSelection selection) {
        this.out.printf("Pedido: %s%n", selection.query());
        this.out.printf("RAG: %s | DAG: %s%n%n", selection.strategyRag(), selection.dagApplied() ? "ligado (ordem de execução)" : "desligado (ordem por nota)");

        for (SelectedTool tool : selection.tools()) {
            String origin = tool.origin() == SelectionOrigin.DAG ? "[DAG] pré-requisito" : String.format(Locale.ROOT, "[RAG] nota %.3f", tool.scoreRag());

            this.out.printf(
                    "  %-34s %-22s %s%n",
                    tool.name(),
                    origin,
                    tool.tool().description()
            );
        }

        this.out.println();
        this.out.printf(
                "%d ferramentas (%d do RAG + %d do DAG) em vez de todas do catálogo.%n",
                selection.tools().size(),
                selection.count(SelectionOrigin.RAG),
                selection.count(SelectionOrigin.DAG)
        );
    }

    public void report(
            AgentRunReport report,
            boolean detailed
    ) {
        this.out.println("Modo: " + report.exposure().label() + " | modelo: " + report.model());
        this.out.println("Pedido: " + report.prompt());
        this.out.println();

        if (detailed) {
            this.steps(report.steps());
        }

        this.out.println("Resposta: " + report.finalAnswer());
        this.out.println();
        this.out.printf("Ferramentas enviadas na 1ª chamada: %d%n", report.toolsSentUpfront());
        this.out.printf(
                Locale.ROOT,
                "Chamadas ao modelo: %d | tokens de entrada: %d (cache: %d) | saída: %d | tempo: %.1fs%n",
                report.modelCalls(),
                report.usage().inputTokens(),
                report.usage().cachedInputTokens(),
                report.usage().outputTokens(),
                report.elapsed().toMillis() / 1000.0
        );
        this.out.printf("Ferramentas executadas: %d | barradas pelo DAG: %d%n", report.toolCalls().size() - report.blockedCalls(), report.blockedCalls());
    }

    public void comparison(
            List<ComparisonRow> rows,
            boolean detailed
    ) {
        if (detailed) {
            for (ComparisonRow row : rows) {
                this.out.println("==== " + row.exposure().alias());

                if (isNull(row.report())) {
                    this.out.println("Erro: " + row.error());
                } else {
                    this.report(row.report(), true);
                }

                this.out.println();
            }
        }

        this.out.println();
        this.out.printf("%-11s %8s %8s %10s %8s %7s %7s %8s  %s%n", "modo", "tools-1ª", "chamadas", "tok.entr.", "tok.saí.", "execs", "barr.", "tempo", "resposta / erro");

        for (ComparisonRow row : rows) {
            this.comparisonLine(row);
        }
    }

    private void comparisonLine(ComparisonRow row) {
        if (isNull(row.report())) {
            this.out.printf("%-11s %8s %8s %10s %8s %7s %7s %8s  ERRO: %s%n", row.exposure().alias(), "-", "-", "-", "-", "-", "-", "-", shorten(row.error()));
            return;
        }

        AgentRunReport report = row.report();

        this.out.printf(
                Locale.ROOT,
                "%-11s %8d %8d %10d %8d %7d %7d %7.1fs  %s%n",
                row.exposure().alias(),
                report.toolsSentUpfront(),
                report.modelCalls(),
                report.usage().inputTokens(),
                report.usage().outputTokens(),
                report.toolCalls().size() - report.blockedCalls(),
                report.blockedCalls(),
                report.elapsed().toMillis() / 1000.0,
                shorten(report.finalAnswer())
        );
    }

    private void steps(List<AgentStep> steps) {
        int index = 1;

        for (AgentStep step : steps) {
            this.out.printf("  %2d. %s%n", index++, this.describe(step));
        }

        this.out.println();
    }

    private String describe(AgentStep step) {
        if (step instanceof AgentStep.HostedSearch hosted) {
            return "tool_search (OpenAI) paths=" + hosted.paths() + " carregou " + hosted.loaded();
        }

        if (step instanceof AgentStep.ClientSearch search) {
            String tools = search.selection().tools().stream()
                    .map(tool -> tool.origin() == SelectionOrigin.DAG ? tool.name() + "*" : tool.name().value())
                    .collect(Collectors.joining(", "));

            return "busca RAG+DAG \"" + search.goal() + "\" → " + tools + "  (* = trazida pelo DAG)";
        }

        AgentStep.ToolCall call = (AgentStep.ToolCall) step;

        if (call.blocked()) {
            return "BARRADA pelo DAG: " + call.tool() + " (falta " + call.missingPrerequisites() + ")";
        }

        return "executou " + call.tool() + " " + call.arguments() + " → " + shorten(call.output());
    }

    private static String shorten(String text) {
        if (isNull(text)) {
            return "";
        }

        String singleLine = text.replaceAll("\\s+", " ");

        return singleLine.length() <= OUTPUT_PREVIEW ? singleLine : singleLine.substring(0, OUTPUT_PREVIEW) + "...";
    }
}
