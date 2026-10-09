package io.github.caiomc.toolsearch.adapters.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentLoaderTest {

    private final EnvironmentLoader loader = new EnvironmentLoader();

    @Test
    void readsKeyValueLinesIgnoringCommentsExportAndQuotes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(".env");
        String content = """
                # comentário
                OPENAI_API_KEY=sk-123

                export OPENAI_MODEL="gpt-5.4-mini"
                OPENAI_BASE_URL='https://proxy/v1'
                LINHA_SEM_IGUAL
                """;

        Files.writeString(file, content);

        assertThat(this.loader.readDotEnv(file)).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        "OPENAI_API_KEY", "sk-123",
                        "OPENAI_MODEL", "gpt-5.4-mini",
                        "OPENAI_BASE_URL", "https://proxy/v1"
                )
        );
    }

    @Test
    void overridesWinOverSystemWhichWinsOverDotEnv(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(".env");

        Files.writeString(file, "A=do-arquivo\nB=do-arquivo\nC=do-arquivo\n");

        Map<String, String> env = this.loader.load(
                Map.of(EnvironmentLoader.ENV_FILE_VARIABLE, file.toString(), "B", "do-sistema", "C", "do-sistema"),
                Map.of("C", "do-cenario")
        );

        assertThat(env).containsEntry("A", "do-arquivo").containsEntry("B", "do-sistema").containsEntry("C", "do-cenario");
    }

    @Test
    void missingFileMeansNoVariables(@TempDir Path dir) {
        assertThat(this.loader.readDotEnv(dir.resolve("nao-existe.env"))).isEmpty();
    }

    @Test
    void envFileCanBeChosenByTheOverrides(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("cenario.env");

        Files.writeString(file, "X=1\n");

        Map<String, String> env = this.loader.load(Map.of(), Map.of(EnvironmentLoader.ENV_FILE_VARIABLE, file.toString()));

        assertThat(env).containsEntry("X", "1");
    }
}
