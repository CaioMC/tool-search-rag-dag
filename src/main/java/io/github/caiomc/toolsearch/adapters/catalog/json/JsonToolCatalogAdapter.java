package io.github.caiomc.toolsearch.adapters.catalog.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.caiomc.toolsearch.adapters.catalog.json.dto.CatalogJson;
import io.github.caiomc.toolsearch.adapters.catalog.json.mapper.CatalogJsonMapper;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.port.source.ToolCatalogPort;

import static java.util.Objects.isNull;

/**
 * Lê o catálogo de um arquivo JSON: primeiro como caminho no disco, depois no classpath.
 * Para testar com as ferramentas reais de outro sistema, gere um JSON no mesmo formato e aponte
 * {@code TOOLSEARCH_CATALOG} para ele.
 */
public final class JsonToolCatalogAdapter implements ToolCatalogPort {

    public static final String DEFAULT_LOCATION = "catalog/erp-tools.json";

    private final String location;

    private final ObjectMapper json;

    private final CatalogJsonMapper mapper;

    public JsonToolCatalogAdapter(String location) {
        this.location = location;
        this.json = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.mapper = new CatalogJsonMapper();
    }

    @Override
    public ToolCatalog load() {
        try (InputStream input = this.open()) {
            return this.mapper.toCatalog(this.json.readValue(input, CatalogJson.class));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler o catálogo de ferramentas em '" + this.location + "': " + e.getMessage(), e);
        }
    }

    private InputStream open() throws IOException {
        Path path = Path.of(this.location);

        if (Files.isRegularFile(path)) {
            return Files.newInputStream(path);
        }

        InputStream resource = JsonToolCatalogAdapter.class.getClassLoader().getResourceAsStream(this.location);

        if (isNull(resource)) {
            throw new IOException("arquivo não encontrado no disco nem no classpath");
        }

        return resource;
    }
}
