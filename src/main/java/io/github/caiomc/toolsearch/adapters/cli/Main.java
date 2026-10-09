package io.github.caiomc.toolsearch.adapters.cli;

import java.util.Map;

import io.github.caiomc.toolsearch.adapters.cli.parser.CliArgumentsParser;
import io.github.caiomc.toolsearch.adapters.config.ApplicationFactory;
import io.github.caiomc.toolsearch.adapters.config.EnvironmentLoader;

/**
 * Ponto de entrada da linha de comando. Exemplos em {@code README.md} ou rodando sem argumentos.
 *
 * <p>Para rodar um caso de teste sem digitar argumentos, use as classes de
 * {@code adapters.cli.scenario.local} (sem chave) e {@code adapters.cli.scenario.model} (com o
 * modelo), que chamam {@link #run} com tudo já preenchido.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(Map.of(), args));
    }

    /**
     * Roda um comando e devolve o código de saída (0 = sucesso), sem encerrar a JVM.
     *
     * @param overrides variáveis que valem só para esta execução (prioridade sobre sistema e .env)
     */
    public static int run(
            Map<String, String> overrides,
            String... args
    ) {
        try {
            Map<String, String> env = new EnvironmentLoader().load(System.getenv(), overrides);
            ApplicationFactory factory = new ApplicationFactory(env);

            factory.cliController(System.out).handle(new CliArgumentsParser().parse(args));

            return 0;
        } catch (RuntimeException e) {
            System.out.flush();
            System.err.println("Erro: " + e.getMessage());
            System.err.flush();

            return 1;
        }
    }
}
