package dev.jobflow.context;

import java.util.List;

public interface EmbeddingProvider {
    String modelVersion();
    List<Float> embed(String text);
}
