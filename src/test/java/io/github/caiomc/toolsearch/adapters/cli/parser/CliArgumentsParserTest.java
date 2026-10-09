package io.github.caiomc.toolsearch.adapters.cli.parser;

import org.junit.jupiter.api.Test;

import io.github.caiomc.toolsearch.adapters.cli.dto.CliArguments;

import static org.assertj.core.api.Assertions.assertThat;

class CliArgumentsParserTest {

    private final CliArgumentsParser parser = new CliArgumentsParser();

    @Test
    void separatesCommandPositionalOptionsAndFlags() {
        CliArguments args = this.parser.parse(new String[] {"buscar", "emitir", "nota", "--top", "3", "--sem-dag"});

        assertThat(args.command()).isEqualTo("buscar");
        assertThat(args.positional()).containsExactly("emitir", "nota");
        assertThat(args.options()).containsEntry("top", "3");
        assertThat(args.flags()).containsExactly("sem-dag");
    }

    @Test
    void knownFlagDoesNotSwallowTheNextArgument() {
        CliArguments args = this.parser.parse(new String[] {"comparar", "--detalhes", "emita a nota"});

        assertThat(args.flags()).containsExactly("detalhes");
        assertThat(args.positional()).containsExactly("emita a nota");
    }

    @Test
    void noArgumentsMeansHelp() {
        assertThat(this.parser.parse(new String[0]).command()).isEqualTo(CliArgumentsParser.HELP_COMMAND);
    }
}
