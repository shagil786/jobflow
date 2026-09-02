package dev.jobflow.context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1")
public class ContextController {
    private final ContextService service;
    private final GroundedGeneration.Gateway gateway;
    private final String internalKey;

    public ContextController(ContextService service, GroundedGeneration.Gateway gateway,
                             @Value("${JOBFLOW_INTERNAL_SERVICE_KEY:}") String internalKey) {
        this.service = service; this.gateway = gateway; this.internalKey = internalKey;
    }

    @PostMapping("/evidence")
    @ResponseStatus(HttpStatus.CREATED)
    public ContextModels.EvidenceDocumentResponse ingest(@RequestHeader(value="X-Internal-Service-Key", required=false) String key,
                                                         @RequestBody ContextModels.EvidenceDocumentRequest request) {
        authorize(key); return service.ingest(request);
    }

    @PostMapping("/context/query")
    public ContextModels.ContextBundle query(@RequestHeader(value="X-Internal-Service-Key", required=false) String key,
                                             @RequestBody ContextModels.ContextQuery request) {
        authorize(key); return service.retrieve(request);
    }

    @PostMapping("/generation")
    public GroundedGeneration.Response generate(@RequestHeader(value="X-Internal-Service-Key", required=false) String key,
                                                @RequestBody GroundedGeneration.Request request) {
        authorize(key); return gateway.generate(request);
    }

    @GetMapping("/service-info")
    public ServiceInfo info(@RequestHeader(value="X-Internal-Service-Key", required=false) String key) {
        authorize(key); return new ServiceInfo("ai-context-service", "tenant-scoped evidence, embeddings, and hybrid retrieval", "ready");
    }

    private void authorize(String provided) {
        if (internalKey.isBlank() || provided == null || !MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "internal authorization failed");
    }
    record ServiceInfo(String service, String responsibility, String status) {}
}
