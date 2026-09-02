package dev.jobflow.context;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContextStore {
    private final JdbcTemplate jdbc;

    public ContextStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public ContextModels.EvidenceDocumentResponse save(ContextModels.EvidenceDocumentRequest request,
                                                       List<String> chunks, List<List<Float>> embeddings,
                                                       String embeddingModel) {
        UUID documentId = UUID.randomUUID();
        String normalized = TextNormalizer.normalize(request.text());
        String contentHash = TextNormalizer.hash(normalized);
        jdbc.update("""
                insert into evidence_documents(id, tenant_id, user_id, source_type, source_id, thread_id,
                    application_id, resume_version_id, occurred_at, content_hash, retention_policy)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (tenant_id, user_id, source_type, source_id, content_hash) do nothing
                """, documentId, request.tenantId(), request.userId(), request.sourceType(), request.sourceId(),
                request.threadId(), request.applicationId(), request.resumeVersionId(),
                request.occurredAt() == null ? null : Timestamp.from(request.occurredAt()), contentHash,
                request.retentionPolicy());
        UUID persistedId = jdbc.query("select id from evidence_documents where tenant_id=? and user_id=? and source_type=? and source_id=? and content_hash=?",
                rs -> rs.next() ? (UUID) rs.getObject(1) : documentId, request.tenantId(), request.userId(), request.sourceType(), request.sourceId(), contentHash);
        for (int i = 0; i < chunks.size(); i++) {
            UUID chunkId = UUID.randomUUID();
            List<Float> embedding = i < embeddings.size() ? embeddings.get(i) : List.of();
            UUID documentKey = persistedId;
            String chunkText = chunks.get(i);
            String chunkHash = TextNormalizer.hash(chunkText);
            int chunkIndex = i;
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        insert into evidence_chunks(id, document_id, tenant_id, user_id, chunk_index, section,
                            text_content, content_hash, embedding_model, embedding_dimensions, embedding)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector)
                        on conflict (document_id, chunk_index) do update set text_content=excluded.text_content,
                            content_hash=excluded.content_hash, embedding_model=excluded.embedding_model,
                            embedding_dimensions=excluded.embedding_dimensions, embedding=excluded.embedding
                        """);
                statement.setObject(1, chunkId); statement.setObject(2, documentKey);
                statement.setString(3, request.tenantId()); statement.setString(4, request.userId());
                statement.setInt(5, chunkIndex); statement.setString(6, "paragraph"); statement.setString(7, chunkText);
                statement.setString(8, chunkHash); statement.setString(9, embeddingModel);
                statement.setObject(10, embedding.isEmpty() ? null : embedding.size());
                statement.setObject(11, embedding.isEmpty() ? null : vector(embedding));
                return statement;
            });
        }
        return new ContextModels.EvidenceDocumentResponse(persistedId, chunks.size(), contentHash, "context-index-v1");
    }

    public List<Float> findCachedEmbedding(String contentHash, String modelVersion) {
        return jdbc.query("select embedding::text from embedding_cache where content_hash=? and model_version=?",
                rs -> rs.next() ? parseVector(rs.getString(1)) : List.of(), contentHash, modelVersion);
    }

    public void cacheEmbedding(String contentHash, String modelVersion, List<Float> embedding) {
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    insert into embedding_cache(content_hash, model_version, dimensions, embedding)
                    values (?, ?, ?, ?::vector)
                    on conflict (content_hash, model_version) do nothing
                    """);
            statement.setString(1, contentHash); statement.setString(2, modelVersion);
            statement.setInt(3, embedding.size()); statement.setObject(4, vector(embedding)); return statement;
        });
    }

    public List<ContextModels.RetrievedEvidence> keywordSearch(ContextModels.ContextQuery query, int limit) {
        StringBuilder sql = new StringBuilder("""
                select c.id, d.source_type, d.source_id, d.thread_id, c.section, c.text_content,
                       ts_rank_cd(to_tsvector('simple', c.text_content), plainto_tsquery('simple', ?)) as score
                from evidence_chunks c join evidence_documents d on d.id=c.document_id
                where c.tenant_id=? and c.user_id=? and to_tsvector('simple', c.text_content) @@ plainto_tsquery('simple', ?)
                """);
        List<Object> args = new ArrayList<>(List.of(query.query(), query.tenantId(), query.userId(), query.query()));
        appendFilters(sql, args, query);
        sql.append(" order by score desc, c.id limit ?"); args.add(limit);
        return jdbc.query(sql.toString(), (rs, row) -> new ContextModels.RetrievedEvidence(
                (UUID) rs.getObject("id"), rs.getString("source_type"), rs.getString("source_id"),
                rs.getString("thread_id"), rs.getString("section"), rs.getString("text_content"),
                rs.getDouble("score"), citation(rs.getString("source_type"), rs.getString("source_id"), row)), args.toArray());
    }

    public List<ContextModels.RetrievedEvidence> vectorSearch(ContextModels.ContextQuery query, List<Float> embedding, int limit) {
        if (embedding == null || embedding.isEmpty()) return List.of();
        StringBuilder sql = new StringBuilder("""
                select c.id, d.source_type, d.source_id, d.thread_id, c.section, c.text_content,
                       1 - (c.embedding <=> ?::vector) as score
                from evidence_chunks c join evidence_documents d on d.id=c.document_id
                where c.tenant_id=? and c.user_id=? and c.embedding is not null
                """);
        List<Object> args = new ArrayList<>();
        args.add(vector(embedding)); args.add(query.tenantId()); args.add(query.userId());
        appendFilters(sql, args, query);
        sql.append(" order by c.embedding <=> ?::vector limit ?"); args.add(vector(embedding)); args.add(limit);
        return jdbc.query(sql.toString(), (rs, row) -> new ContextModels.RetrievedEvidence(
                (UUID) rs.getObject("id"), rs.getString("source_type"), rs.getString("source_id"),
                rs.getString("thread_id"), rs.getString("section"), rs.getString("text_content"),
                rs.getDouble("score"), citation(rs.getString("source_type"), rs.getString("source_id"), row)), args.toArray());
    }

    private static void appendFilters(StringBuilder sql, List<Object> args, ContextModels.ContextQuery query) {
        if (!query.sourceTypes().isEmpty()) {
            sql.append(" and d.source_type = any (?)"); args.add(query.sourceTypes().toArray(String[]::new));
        }
        if (query.threadId() != null && !query.threadId().isBlank()) { sql.append(" and d.thread_id=?"); args.add(query.threadId()); }
        if (query.applicationId() != null && !query.applicationId().isBlank()) { sql.append(" and d.application_id=?"); args.add(query.applicationId()); }
        if (query.resumeVersionId() != null && !query.resumeVersionId().isBlank()) { sql.append(" and d.resume_version_id=?"); args.add(query.resumeVersionId()); }
    }

    private static String citation(String sourceType, String sourceId, int row) { return sourceType + ":" + sourceId + "#" + row; }

    private static List<Float> parseVector(String value) {
        if (value == null || value.length() < 2) return List.of();
        String body = value.substring(1, value.length() - 1).trim();
        if (body.isEmpty()) return List.of();
        return java.util.Arrays.stream(body.split(",")).map(String::trim).map(Float::valueOf).toList();
    }

    private static PGobject vector(List<Float> values) {
        try {
            PGobject object = new PGobject(); object.setType("vector");
            object.setValue("[" + values.stream().map(String::valueOf).reduce((a,b) -> a + "," + b).orElse("") + "]");
            return object;
        } catch (SQLException exception) {
            throw new IllegalStateException("invalid vector", exception);
        }
    }
}
