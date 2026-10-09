package io.github.caiomc.toolsearch.adapters.cli.mapper;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.adapters.cli.dto.CliArguments;
import io.github.caiomc.toolsearch.core.agent.domain.enums.ToolExposure;
import io.github.caiomc.toolsearch.core.agent.usecase.command.RunAgentCommand;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.RetrievalStrategyRag;
import io.github.caiomc.toolsearch.core.retrieval.usecase.command.SelectToolsCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliCommandMapperTest {

    private final CliCommandMapper mapper = new CliCommandMapper();

    @Test
    void selectCommandUsesDefaultsAndTurnsSemDagIntoNoExpansion() {
        CliArguments args = new CliArguments(
                "buscar",
                List.of("emitir", "nota"),
                Map.of(),
                Set.of("sem-dag")
        );

        SelectToolsCommand command = this.mapper.toSelectCommand(args);

        assertThat(command.query()).isEqualTo("emitir nota");
        assertThat(command.strategyRag()).isEqualTo(RetrievalStrategyRag.BM25);
        assertThat(command.topKRag()).isEqualTo(CliCommandMapper.DEFAULT_TOP_K);
        assertThat(command.useDag()).isFalse();
    }

    @Test
    void runAgentCommandReadsStrategyAndTopK() {
        CliArguments args = new CliArguments(
                "agente",
                List.of("pague o título"),
                Map.of("estrategia", "embedding", "top", "8", "modo", "hosted"),
                Set.of()
        );

        RunAgentCommand command = this.mapper.toRunAgentCommand(args, this.mapper.toExposure(args));

        assertThat(command.exposure()).isEqualTo(ToolExposure.HOSTED_TOOL_SEARCH);
        assertThat(command.strategyRag()).isEqualTo(RetrievalStrategyRag.EMBEDDING);
        assertThat(command.topKRag()).isEqualTo(8);
    }

    @Test
    void exposuresListDefaultsToAllModes() {
        CliArguments args = new CliArguments(
                "comparar",
                List.of("x"),
                Map.of(),
                Set.of()
        );

        assertThat(this.mapper.toExposures(args)).containsExactly(ToolExposure.values());
    }

    @Test
    void reportsFormatErrorsClearly() {
        CliArguments noText = new CliArguments("buscar", List.of(), Map.of(), Set.of());
        CliArguments badTop = new CliArguments("buscar", List.of("x"), Map.of("top", "muitos"), Set.of());

        assertThatThrownBy(() -> this.mapper.toSelectCommand(noText)).hasMessageContaining("Faltou o texto");
        assertThatThrownBy(() -> this.mapper.toSelectCommand(badTop)).hasMessageContaining("--top");
    }
}
