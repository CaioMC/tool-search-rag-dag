package io.github.caiomc.toolsearch.adapters.cli.mapper;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import io.github.caiomc.toolsearch.adapters.cli.dto.CliArguments;
import io.github.caiomc.toolsearch.core.agent.domain.enums.ToolExposure;
import io.github.caiomc.toolsearch.core.agent.usecase.command.RunAgentCommand;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.RetrievalStrategyRag;
import io.github.caiomc.toolsearch.core.retrieval.usecase.command.SelectToolsCommand;

/**
 * Converte os argumentos da linha de comando nos comandos dos casos de uso. Validação de
 * formato (texto ausente, estratégia desconhecida) fica aqui; validação de valor fica nos comandos.
 */
public final class CliCommandMapper {

    public static final int DEFAULT_TOP_K = 5;

    public static final String DEFAULT_EXPOSURES = "prompt,todas,preselecao,hosted,client";

    public SelectToolsCommand toSelectCommand(CliArguments args) {
        return new SelectToolsCommand(
                this.text(args),
                this.strategy(args),
                this.topKRag(args),
                !args.flags().contains("sem-dag")
        );
    }

    public RunAgentCommand toRunAgentCommand(
            CliArguments args,
            ToolExposure exposure
    ) {
        return new RunAgentCommand(
                this.text(args),
                exposure,
                this.strategy(args),
                this.topKRag(args)
        );
    }

    public ToolName toToolName(CliArguments args) {
        return ToolName.of(this.text(args));
    }

    public ToolExposure toExposure(CliArguments args) {
        return ToolExposure.fromAlias(args.options().getOrDefault("modo", ToolExposure.CLIENT_TOOL_SEARCH.alias()));
    }

    public List<ToolExposure> toExposures(CliArguments args) {
        return Arrays.stream(args.options().getOrDefault("modos", DEFAULT_EXPOSURES).split(","))
                .map(String::trim)
                .map(ToolExposure::fromAlias)
                .toList();
    }

    public boolean detailed(CliArguments args) {
        return args.flags().contains("detalhes");
    }

    private String text(CliArguments args) {
        if (args.positional().isEmpty()) {
            throw new IllegalArgumentException("Faltou o texto do comando '" + args.command() + "'. Exemplo: ./toolsearch " + args.command() + " \"emitir a nota fiscal do pedido 42\"");
        }

        return String.join(" ", args.positional());
    }

    private RetrievalStrategyRag strategy(CliArguments args) {
        String value = args.options().getOrDefault("estrategia", "bm25");

        try {
            return RetrievalStrategyRag.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estratégia desconhecida: '" + value + "'. Use bm25 ou embedding.", e);
        }
    }

    private int topKRag(CliArguments args) {
        String value = args.options().getOrDefault("top", String.valueOf(DEFAULT_TOP_K));

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--top precisa ser um número inteiro, mas foi '" + value + "'.", e);
        }
    }
}
