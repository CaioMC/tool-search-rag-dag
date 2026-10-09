package io.github.caiomc.toolsearch.adapters.cli.scenario.support;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import io.github.caiomc.toolsearch.adapters.cli.Main;

/**
 * Apoio dos cenários de teste de {@code docs/como-testar.md}: cada cenário é uma classe com
 * {@code main} que roda pela IDE sem argumentos. Antes de rodar, imprime o comando equivalente
 * para o terminal, assim dá para repetir com {@code ./toolsearch}.
 */
public final class Scenario {

    public static final String MINI_CATALOG = "catalog/exemplos/catalogo-mini.json";

    public static final String CYCLE_CATALOG = "catalog/exemplos/catalogo-com-ciclo.json";

    public static final String UNKNOWN_DEPENDENCY_CATALOG = "catalog/exemplos/catalogo-dependencia-inexistente.json";

    public static final String THOUSAND_TOOLS_CATALOG = "catalog/exemplos/catalogo-1000.json";

    public static final String NOTA_FISCAL = "Emita a nota fiscal do pedido de venda 42";

    private static final String RULE = "=".repeat(100);

    private Scenario() {
    }

    /**
     * Cabeçalho do cenário com o checklist do que observar na saída.
     */
    public static void title(
            String title,
            String... observe
    ) {
        System.out.println(RULE);
        System.out.println(title);
        System.out.println(RULE);

        if (observe.length > 0) {
            System.out.println("O que observar:");
            Arrays.stream(observe).forEach(item -> System.out.println("  [ ] " + item));
        }

        System.out.println();
    }

    /**
     * Roda um comando da CLI com variáveis próprias (prioridade sobre sistema e .env).
     */
    public static int command(
            Map<String, String> env,
            String... args
    ) {
        System.out.println("$ " + commandLine(env, args));
        System.out.println();

        int exitCode = Main.run(env, args);

        System.out.println();

        return exitCode;
    }

    public static int command(String... args) {
        return command(Map.of(), args);
    }

    private static String commandLine(
            Map<String, String> env,
            String[] args
    ) {
        String variables = env.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue() + " ").sorted().collect(Collectors.joining());
        String arguments = Arrays.stream(args).map(arg -> arg.contains(" ") ? "\"" + arg + "\"" : arg).collect(Collectors.joining(" "));

        return variables + "./toolsearch " + arguments;
    }
}
