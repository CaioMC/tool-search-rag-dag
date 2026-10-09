package io.github.caiomc.toolsearch.adapters.cli.dto;

import io.github.caiomc.toolsearch.core.agent.domain.enums.ToolExposure;
import io.github.caiomc.toolsearch.core.agent.usecase.result.AgentRunReport;

/**
 * Uma linha da tabela do comando {@code comparar}: o relatório ou o erro daquele modo.
 */
public record ComparisonRow(
        ToolExposure exposure,
        AgentRunReport report,
        String error
) {
}
