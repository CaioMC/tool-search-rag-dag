package io.github.caiomc.toolsearch.adapters.cli;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.adapters.cli.scenario.support.Scenario;
import io.github.caiomc.toolsearch.adapters.config.EnvironmentLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executa a CLI de ponta a ponta, sem rede (só comandos locais).
 */
class MainTest {

    private static final Map<String, String> NO_DOT_ENV = Map.of(EnvironmentLoader.ENV_FILE_VARIABLE, "/nao/existe.env");

    @Test
    void localCommandSucceeds() {
        assertThat(Main.run(NO_DOT_ENV, "plano", "emitir_nota_fiscal")).isZero();
    }

    @Test
    void exampleCatalogsAreLoadedFromTheClasspath() {
        Map<String, String> env = Map.of(
                EnvironmentLoader.ENV_FILE_VARIABLE, "/nao/existe.env",
                "TOOLSEARCH_CATALOG", Scenario.MINI_CATALOG
        );

        assertThat(Main.run(env, "plano", "aprovar_pedido")).isZero();
    }

    @Test
    void invalidCatalogOrArgumentReturnsOneInsteadOfExiting() {
        Map<String, String> cycle = Map.of(
                EnvironmentLoader.ENV_FILE_VARIABLE, "/nao/existe.env",
                "TOOLSEARCH_CATALOG", Scenario.CYCLE_CATALOG
        );

        assertThat(Main.run(cycle, "catalogo")).isEqualTo(1);
        assertThat(Main.run(NO_DOT_ENV, "plano", "nao_existe")).isEqualTo(1);
    }

    @Test
    void agentWithoutKeyFailsWithClearMessage() {
        assertThat(Main.run(Map.of(EnvironmentLoader.ENV_FILE_VARIABLE, "/nao/existe.env", "OPENAI_API_KEY", ""), "agente", "x")).isEqualTo(1);
    }
}
