package io.github.caiomc.toolsearch.adapters.shared.openai;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiSettingsTest {

    @Test
    void onlyTheKeyIsNeededToUseTheOfficialApi() {
        OpenAiSettings settings = OpenAiSettings.fromEnvironment(Map.of("OPENAI_API_KEY", "sk-123"));

        assertThat(settings.baseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(settings.model()).isEqualTo(OpenAiSettings.DEFAULT_MODEL);
        assertThat(settings.embeddingModel()).isEqualTo(OpenAiSettings.DEFAULT_EMBEDDING_MODEL);
        assertThat(settings.timeout()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void blankVariablesFallBackToDefaults() {
        OpenAiSettings settings = OpenAiSettings.fromEnvironment(
                Map.of(
                        "OPENAI_API_KEY", "sk-123",
                        "OPENAI_BASE_URL", "",
                        "OPENAI_MODEL", " ",
                        "OPENAI_TIMEOUT_SECONDS", ""
                )
        );

        assertThat(settings.baseUrl()).isEqualTo(OpenAiSettings.DEFAULT_BASE_URL);
        assertThat(settings.model()).isEqualTo(OpenAiSettings.DEFAULT_MODEL);
        assertThat(settings.timeout()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void compatibleServerReplacesTheOfficialUrl() {
        OpenAiSettings settings = OpenAiSettings.fromEnvironment(
                Map.of(
                        "OPENAI_API_KEY", "sk-123",
                        "OPENAI_BASE_URL", "http://localhost:4000/v1/"
                )
        );

        assertThat(settings.baseUrl()).isEqualTo("http://localhost:4000/v1");
    }

    @Test
    void missingKeyExplainsHowToConfigureIt() {
        assertThatThrownBy(() -> OpenAiSettings.fromEnvironment(Map.of("OPENAI_API_KEY", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(".env.example")
                .hasMessageContaining("OPENAI_API_KEY");
    }
}
