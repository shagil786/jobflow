package dev.jobflow.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;

final class ClassificationSuggestionJsonCodec {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<ExtractedFieldCandidateV1<String>> STRING_CANDIDATE =
            new TypeReference<>() {};
    private static final TypeReference<List<EvidenceSpanV1>> EVIDENCE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private ClassificationSuggestionJsonCodec() {}

    static String write(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize classification suggestion payload", exception);
        }
    }

    static ExtractedFieldCandidateV1<String> readStringCandidate(String json) {
        return read(json, STRING_CANDIDATE);
    }

    static List<EvidenceSpanV1> readEvidenceList(String json) {
        List<EvidenceSpanV1> evidence = read(json, EVIDENCE_LIST);
        return evidence == null ? List.of() : evidence;
    }

    static List<String> readStringList(String json) {
        List<String> values = read(json, STRING_LIST);
        return values == null ? List.of() : values;
    }

    private static <T> T read(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to deserialize classification suggestion payload", exception);
        }
    }
}
