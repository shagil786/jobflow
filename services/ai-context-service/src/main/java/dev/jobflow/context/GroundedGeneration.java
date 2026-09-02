package dev.jobflow.context;

import java.util.List;
import java.util.Map;

public final class GroundedGeneration {
    private GroundedGeneration() {}
    public record Request(String tenantId, String userId, String correlationId, String purpose,
                          String instruction, ContextModels.ContextBundle context, Map<String,Object> schema) {}
    public record Response(String content, List<String> citations, ContextModels.VerificationResult verification,
                           String modelVersion, boolean sent) {}
    public interface Gateway { Response generate(Request request); }
}
