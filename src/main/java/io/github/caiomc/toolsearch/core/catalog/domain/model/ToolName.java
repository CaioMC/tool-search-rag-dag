package io.github.caiomc.toolsearch.core.catalog.domain.model;

import java.util.regex.Pattern;

import static java.util.Objects.isNull;

/**
 * Nome de uma ferramenta, no formato aceito pela OpenAI para funções (snake_case).
 */
public record ToolName(String value) implements Comparable<ToolName> {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    public ToolName {
        if (isNull(value) || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Nome de ferramenta inválido: '" + value + "'. Use snake_case com letras minúsculas, números e '_' (até 64 caracteres).");
        }
    }

    public static ToolName of(String value) {
        return new ToolName(value);
    }

    @Override
    public int compareTo(ToolName other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return this.value;
    }
}
