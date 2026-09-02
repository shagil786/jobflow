package dev.jobflow.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ContextService {
    private final ContextStore store;
    private final EmbeddingProvider embeddings;

    public ContextService(ContextStore store, EmbeddingProvider embeddings) {
        this.store = store; this.embeddings = embeddings;
    }

    public ContextModels.EvidenceDocumentResponse ingest(ContextModels.EvidenceDocumentRequest request) {
        String normalized = TextNormalizer.normalize(request.text());
        List<String> chunks = SemanticChunker.chunk(normalized);
        List<List<Float>> vectors = chunks.stream().map(chunk -> embeddingFor(chunk)).toList();
        return store.save(request, chunks, vectors, embeddings.modelVersion());
    }

    private List<Float> embeddingFor(String text) {
        String hash = TextNormalizer.hash(text);
        List<Float> cached = store.findCachedEmbedding(hash, embeddings.modelVersion());
        if (!cached.isEmpty()) return cached;
        List<Float> generated = embeddings.embed(text);
        if (!generated.isEmpty()) store.cacheEmbedding(hash, embeddings.modelVersion(), generated);
        return generated;
    }

    public ContextModels.ContextBundle retrieve(ContextModels.ContextQuery query) {
        List<ContextModels.RetrievedEvidence> keyword = store.keywordSearch(query, Math.min(50, query.limit() * 4));
        List<ContextModels.RetrievedEvidence> vector = store.vectorSearch(query, embeddings.embed(query.query()), Math.min(50, query.limit() * 4));
        Map<String, Scored> merged = new LinkedHashMap<>();
        keyword.forEach(item -> merged.put(key(item), new Scored(item, normalizeKeyword(item.score()), 0)));
        vector.forEach(item -> merged.merge(key(item), new Scored(item, 0, normalizeVector(item.score())),
                (left, right) -> new Scored(left.item(), left.keyword(), right.vector())));
        List<ContextModels.RetrievedEvidence> selected = merged.values().stream()
                .sorted(Comparator.comparingDouble(Scored::combined).reversed())
                .map(Scored::item).limit(query.limit()).toList();
        int tokens = selected.stream().mapToInt(item -> Math.max(1, item.text().length() / 4)).sum();
        List<ContextModels.RetrievedEvidence> bounded = new ArrayList<>(); int used = 0;
        for (ContextModels.RetrievedEvidence item : selected) {
            int cost = Math.max(1, item.text().length() / 4);
            if (used + cost > query.maxTokens()) break;
            bounded.add(item); used += cost;
        }
        ContextModels.ReviewState state = bounded.isEmpty() ? ContextModels.ReviewState.UNKNOWN : ContextModels.ReviewState.SUPPORTED;
        List<String> warnings = bounded.isEmpty() ? List.of("no evidence matched the scoped query") : List.of();
        return new ContextModels.ContextBundle(query, bounded, Math.min(tokens, query.maxTokens()), state, warnings);
    }

    private static String key(ContextModels.RetrievedEvidence item) { return item.chunkId().toString(); }
    private static double normalizeKeyword(double score) { return Math.min(1, score); }
    private static double normalizeVector(double score) { return Math.max(0, Math.min(1, score)); }
    private record Scored(ContextModels.RetrievedEvidence item, double keyword, double vector) {
        double combined() { return (keyword * 0.35) + (vector * 0.65); }
    }
}
