create table if not exists gmail_message_candidates (
    candidate_id uuid primary key,
    connection_id uuid not null,
    tenant_id varchar(120) not null,
    user_id varchar(120) not null,
    provider_message_id varchar(255) not null,
    run_id uuid,
    batch_id uuid,
    state varchar(32) not null,
    deterministic_score double precision not null,
    deterministic_signals_json text not null,
    discovered_at timestamp with time zone not null,
    expires_at timestamp with time zone,
    constraint fk_candidates_connection foreign key (connection_id) references gmail_connections(connection_id),
    constraint fk_candidates_run foreign key (run_id) references gmail_backfill_runs(run_id),
    constraint fk_candidates_batch foreign key (batch_id) references gmail_backfill_batches(batch_id),
    constraint ux_candidates_provider_identity unique (connection_id, provider_message_id),
    constraint ck_candidates_state check (state in ('PENDING','ENRICHING','READY_FOR_REVIEW','CONFIRMED','DISMISSED','EXPIRED'))
);
create index if not exists ix_candidates_owner_state on gmail_message_candidates (tenant_id, user_id, state);
create index if not exists ix_candidates_batch on gmail_message_candidates (batch_id, state);
