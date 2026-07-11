package com.suplab.aether.memory.engine.embedding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Generates 384-dimension text embeddings via Ollama's {@code /api/embeddings} endpoint.
 *
 * <p>Uses the {@code all-minilm} model (all-MiniLM-L6-v2) which produces 384-dimension vectors
 * compatible with the pgvector IVFFlat index in {@code shared_memories}. This dimension is a
 * hard constraint shared across the whole Aether ecosystem — changing it requires a full
 * re-embedding migration.</p>
 *
 * <p>On any failure (network error, model unavailable, malformed response) this service returns
 * a zero vector rather than propagating an exception. Callers that need to detect embedding
 * failure should check whether the returned vector is all zeros.</p>
 */
public class SharedEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(SharedEmbeddingService.class);
    private static final int EMBEDDING_DIM = 384;

    private final RestClient restClient;
    private final String model;

    public SharedEmbeddingService(String ollamaBaseUrl, String model) {
        this.restClient = RestClient.builder().baseUrl(ollamaBaseUrl).build();
        this.model = model;
    }

    /**
     * Embeds the given text and returns a 384-dimension float array.
     * Returns a zero vector on failure without throwing.
     *
     * @param text the content to embed
     * @return 384-dimension embedding vector, or a zero vector on failure
     */
    @SuppressWarnings("unchecked")
    public float[] embed(String text) {
        try {
            var response = restClient.post()
                    .uri("/api/embeddings")
                    .body(Map.of("model", model, "prompt", text))
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                log.warn("Null response from Ollama embeddings endpoint for model={}", model);
                return zeroVector();
            }
            var embedding = (List<Double>) response.get("embedding");
            if (embedding == null || embedding.isEmpty()) {
                log.warn("Empty embedding returned by Ollama for model={}", model);
                return zeroVector();
            }
            var result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            log.warn("Embedding request failed for model={} textLength={}: {}", model, text.length(), e.getMessage());
            return zeroVector();
        }
    }

    private static float[] zeroVector() {
        return new float[EMBEDDING_DIM];
    }
}
