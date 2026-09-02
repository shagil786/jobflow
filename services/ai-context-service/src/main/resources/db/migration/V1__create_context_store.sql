create extension if not exists vector;

create table if not exists evidence_documents (
    id uuid primary key,
    tenant_id varchar(255) not null,
    user_id varchar(255) not null,
    source_type varchar(64) not null,
    source_id varchar(255) not null,
    thread_id varchar(255),
    application_id varchar(255),
    resume_version_id varchar(255),
    occurred_at timestamptz,
    content_hash varchar(128) not null,
    retention_policy varchar(64) not null,
    created_at timestamptz not null default current_timestamp,
    unique (tenant_id, user_id, source_type, source_id, content_hash)
);

create table if not exists evidence_chunks (
    id uuid primary key,
    document_id uuid not null references evidence_documents(id) on delete cascade,
    tenant_id varchar(255) not null,
    user_id varchar(255) not null,
    chunk_index integer not null,
    section varchar(128),
    text_content text not null,
    content_hash varchar(128) not null,
    embedding_model varchar(128),
    embedding_dimensions integer,
    embedding vector(1536),
    created_at timestamptz not null default current_timestamp,
    unique (document_id, chunk_index)
);

create table if not exists embedding_cache (
    content_hash varchar(128) not null,
    model_version varchar(128) not null,
    dimensions integer not null,
    embedding vector(1536) not null,
    created_at timestamptz not null default current_timestamp,
    primary key (content_hash, model_version)
);

create index if not exists idx_evidence_documents_owner on evidence_documents (tenant_id, user_id, source_type, occurred_at desc);
create index if not exists idx_evidence_chunks_owner on evidence_chunks (tenant_id, user_id, document_id);
create index if not exists idx_evidence_chunks_text on evidence_chunks using gin (to_tsvector('simple', text_content));
create index if not exists idx_evidence_chunks_embedding on evidence_chunks using hnsw (embedding vector_cosine_ops);
