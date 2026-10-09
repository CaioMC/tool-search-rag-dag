package io.github.caiomc.toolsearch.core.agent.usecase.result;

import java.time.Duration;
import java.util.List;

import io.github.caiomc.toolsearch.core.agent.domain.enums.ToolExposure;
import io.github.caiomc.toolsearch.core.agent.domain.model.TokenUsage;

/**
 * Resultado de uma execução do agente: a resposta e as métricas que a POC compara.
 */
public record AgentRunReport(
        String prompt,
        ToolExposure exposure,
        String model,
        int toolsSentUpfront,
        String finalAnswer,
        List<AgentStep> steps,
        int modelCalls,
        TokenUsage usage,
        Duration elapsed
) {

    public AgentRunReport {
        steps = List.copyOf(steps);
    }

    public List<AgentStep.ToolCall> toolCalls() {
        return this.steps.stream().filter(AgentStep.ToolCall.class::isInstance).map(AgentStep.ToolCall.class::cast).toList();
    }

    public long blockedCalls() {
        return this.toolCalls().stream().filter(AgentStep.ToolCall::blocked).count();
    }

    public long searches() {
        return this.steps.stream().filter(step -> !(step instanceof AgentStep.ToolCall)).count();
    }
}
