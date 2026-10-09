package io.github.caiomc.toolsearch.adapters.catalog.json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.caiomc.toolsearch.core.catalog.domain.exception.UnknownDependencyException;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDependencyDag;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonToolCatalogAdapterTest {

    @Test
    void loadsTheBundledCatalogWith250ToolsAndAValidDag() {
        ToolCatalog catalog = new JsonToolCatalogAdapter(JsonToolCatalogAdapter.DEFAULT_LOCATION).load();
        ToolDependencyDag dag = ToolDependencyDag.of(catalog);

        assertThat(catalog.size()).isEqualTo(250);
        assertThat(catalog.namespaces()).hasSize(50);
        assertThat(catalog.require(ToolName.of("emitir_nota_fiscal")).dependsOn()).contains(ToolName.of("calcular_impostos_operacao"));
        assertThat(dag.edgeCount()).isPositive();
    }

    @Test
    void readsACatalogFromDisk(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("catalogo.json");
        String content = """
                {"tools": [
                  {"name": "consultar_cliente", "namespace": "vendas", "description": "Consulta cliente."},
                  {"name": "aprovar_credito", "namespace": "vendas", "description": "Aprova crédito.", "dependsOn": ["consultar_cliente"]}
                ]}
                """;

        Files.writeString(file, content);

        ToolCatalog catalog = new JsonToolCatalogAdapter(file.toString()).load();

        assertThat(catalog.size()).isEqualTo(2);
    }

    @Test
    void reportsUnknownDependencyFromTheFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("catalogo.json");
        String content = """
                {"tools": [{"name": "aprovar_credito", "namespace": "vendas", "description": "Aprova.", "dependsOn": ["nao_existe"]}]}
                """;

        Files.writeString(file, content);

        assertThatThrownBy(() -> new JsonToolCatalogAdapter(file.toString()).load()).isInstanceOf(UnknownDependencyException.class);
    }

    @Test
    void reportsMissingFile() {
        assertThatThrownBy(() -> new JsonToolCatalogAdapter("nao/existe.json").load())
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("nao/existe.json");
    }
}
