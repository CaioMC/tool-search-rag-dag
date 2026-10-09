package io.github.caiomc.toolsearch.core.retrieval.application.rag;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolCatalog;
import io.github.caiomc.toolsearch.core.catalog.domain.model.ToolDefinition;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.RetrievalStrategyRag;
import io.github.caiomc.toolsearch.core.retrieval.domain.enums.SelectionOrigin;
import io.github.caiomc.toolsearch.core.retrieval.usecase.result.SelectedTool;

/**
 * RAG lexical com BM25, o mesmo ranking clássico de motores como Lucene/Elasticsearch.
 *
 * <p>Premia termos raros que aparecem na ferramenta ("sefaz" vale mais que "consultar") e
 * penaliza descrições longas. Não entende sinônimos: "NF" não casa com "nota fiscal".
 */
public final class Bm25RetrieverRag implements ToolRetrieverRag {

    private static final double K1 = 1.2;

    private static final double B = 0.75;

    private final List<IndexedTool> documents;

    private final Map<String, Integer> documentFrequency;

    private final double averageLength;

    public Bm25RetrieverRag(ToolCatalog catalog) {
        this.documents = catalog.all().stream().map(IndexedTool::of).toList();
        this.documentFrequency = new HashMap<>();

        for (IndexedTool document : this.documents) {
            for (String term : new HashSet<>(document.terms())) {
                this.documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        this.averageLength = this.documents.stream().mapToInt(IndexedTool::length).average().orElse(1);
    }

    @Override
    public RetrievalStrategyRag strategy() {
        return RetrievalStrategyRag.BM25;
    }

    @Override
    public List<SelectedTool> retrieve(
            String query,
            int topKRag
    ) {
        List<String> queryTerms = TextTokenizer.tokenize(query);

        return this.documents.stream()
                .map(document -> new SelectedTool(document.tool(), this.score(document, queryTerms), SelectionOrigin.RAG))
                .filter(selected -> selected.scoreRag() > 0)
                .sorted(Comparator.comparingDouble(SelectedTool::scoreRag).reversed().thenComparing(SelectedTool::name))
                .limit(topKRag)
                .toList();
    }

    private double score(
            IndexedTool document,
            List<String> queryTerms
    ) {
        double score = 0;

        for (String term : queryTerms) {
            int frequency = document.frequencies().getOrDefault(term, 0);

            if (frequency == 0) {
                continue;
            }

            double idf = this.idf(term);
            double normalization = K1 * (1 - B + B * document.length() / this.averageLength);

            score += idf * frequency * (K1 + 1) / (frequency + normalization);
        }

        return score;
    }

    private double idf(String term) {
        int total = this.documents.size();
        int containing = this.documentFrequency.getOrDefault(term, 0);

        return Math.log(1 + (total - containing + 0.5) / (containing + 0.5));
    }

    private record IndexedTool(
            ToolDefinition tool,
            List<String> terms,
            Map<String, Integer> frequencies
    ) {

        static IndexedTool of(ToolDefinition tool) {
            List<String> terms = TextTokenizer.tokenize(tool.searchableText());
            Map<String, Integer> frequencies = new HashMap<>();

            for (String term : terms) {
                frequencies.merge(term, 1, Integer::sum);
            }

            return new IndexedTool(
                    tool,
                    terms,
                    frequencies
            );
        }

        int length() {
            return this.terms.size();
        }
    }
}
