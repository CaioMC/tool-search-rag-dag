package io.github.caiomc.toolsearch.adapters.cli.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Argumentos da linha de comando já separados: {@code <comando> [posicionais] [--opcao valor] [--flag]}.
 * Só dados; quem monta é o {@code CliArgumentsParser} e quem interpreta é o {@code CliCommandMapper}.
 */
public record CliArguments(
        String command,
        List<String> positional,
        Map<String, String> options,
        Set<String> flags
) {
}
