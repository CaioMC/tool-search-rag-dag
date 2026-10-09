package io.github.caiomc.toolsearch.core.catalog.domain.model;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.core.catalog.domain.exception.CyclicDependencyException;
import io.github.caiomc.toolsearch.core.catalog.domain.exception.UnknownDependencyException;

import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.billingCatalog;
import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.name;
import static io.github.caiomc.toolsearch.core.ToolSearchTestDoubles.tool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolDependencyDagTest {

    private final ToolDependencyDag dag = ToolDependencyDag.of(billingCatalog());

    @Test
    void withAllPrerequisitesIncludesTransitivePrerequisites() {
        Set<ToolName> withPrerequisites = this.dag.withAllPrerequisites(List.of(name("emitir_nota_fiscal")));

        assertThat(withPrerequisites).containsExactlyInAnyOrder(
                name("emitir_nota_fiscal"),
                name("consultar_pedido_venda"),
                name("calcular_impostos_operacao"),
                name("determinar_cfop_operacao")
        );
    }

    @Test
    void executionOrderPutsPrerequisitesFirst() {
        List<ToolName> order = this.dag.executionOrder(List.of(name("emitir_nota_fiscal")));

        assertThat(order).containsExactly(
                name("consultar_pedido_venda"),
                name("determinar_cfop_operacao"),
                name("calcular_impostos_operacao"),
                name("emitir_nota_fiscal")
        );
    }

    @Test
    void missingPrerequisitesAreOnlyTheDirectOnesNotExecuted() {
        Set<ToolName> missing = this.dag.missingPrerequisites(name("emitir_nota_fiscal"), Set.of(name("consultar_pedido_venda")));

        assertThat(missing).containsExactly(name("calcular_impostos_operacao"));
    }

    @Test
    void depthIsTheLongestChain() {
        assertThat(this.dag.depthOf(name("emitir_nota_fiscal"))).isEqualTo(2);
        assertThat(this.dag.depthOf(name("cadastrar_funcionario"))).isZero();
        assertThat(this.dag.edgeCount()).isEqualTo(3);
    }

    @Test
    void rejectsCycles() {
        ToolCatalog cyclic = new ToolCatalog(
                List.of(),
                List.of(
                        tool("a_tool", "A.", "b_tool"),
                        tool("b_tool", "B.", "c_tool"),
                        tool("c_tool", "C.", "a_tool")
                )
        );

        assertThatThrownBy(() -> ToolDependencyDag.of(cyclic))
                .isInstanceOf(CyclicDependencyException.class)
                .hasMessageContaining("a_tool -> b_tool -> c_tool -> a_tool");
    }

    @Test
    void catalogRejectsUnknownDependency() {
        assertThatThrownBy(() -> new ToolCatalog(List.of(), List.of(tool("a_tool", "A.", "nao_existe"))))
                .isInstanceOf(UnknownDependencyException.class)
                .hasMessageContaining("nao_existe");
    }

    @Test
    void catalogRejectsDuplicatedNames() {
        assertThatThrownBy(() -> new ToolCatalog(List.of(), List.of(tool("a_tool", "A."), tool("a_tool", "B."))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicada");
    }

    @Test
    void toolNameMustBeSnakeCase() {
        assertThatThrownBy(() -> ToolName.of("Emitir Nota"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("snake_case");
    }
}
