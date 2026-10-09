package io.github.caiomc.toolsearch.adapters.cli.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.caiomc.toolsearch.adapters.cli.dto.CliArguments;

/**
 * Separa {@code String[] args} em comando, posicionais, opções ({@code --nome valor}) e flags
 * ({@code --nome} sem valor).
 */
public final class CliArgumentsParser {

    public static final String HELP_COMMAND = "ajuda";

    private static final Set<String> FLAGS = Set.of("sem-dag", "detalhes");

    public CliArguments parse(String[] args) {
        List<String> positional = new ArrayList<>();
        Map<String, String> options = new HashMap<>();
        Set<String> flags = new HashSet<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (!arg.startsWith("--")) {
                positional.add(arg);
                continue;
            }

            String name = arg.substring(2);

            if (FLAGS.contains(name) || i + 1 >= args.length) {
                flags.add(name);
            } else {
                options.put(name, args[++i]);
            }
        }

        String command = positional.isEmpty() ? HELP_COMMAND : positional.remove(0);

        return new CliArguments(
                command,
                List.copyOf(positional),
                Map.copyOf(options),
                Set.copyOf(flags)
        );
    }
}
