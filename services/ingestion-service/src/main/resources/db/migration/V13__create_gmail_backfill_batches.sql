create table if not exists gmail_backfill_batches (
    batch_id uuid primary key,
    run_id uuid not null,
    tenant_id varchar(120) not null,
    user_id varchar(120) not null,
    connection_id uuid not null,
    sequence_no integer not null,
    window_from timestamp with time zone not null,
    window_to timestamp with time zone not null,
    priority varchar(16) not null,
    status varchar(32) not null,
    attempt_count integer not null default 0,
    imported_messages integer not null default 0,
    candidate_messages integer not null default 0,
    last_error_code varchar(64),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_backfill_batches_run foreign key (run_id) references gmail_backfill_runs(run_id),
    constraint fk_backfill_batches_connection foreign key (connection_id) references gmail_connections(connection_id),
    constraint ck_backfill_batches_range check (window_to > window_from),
    constraint ck_backfill_batches_priority check (priority in ('HIGH','NORMAL')),
    constraint ck_backfill_batches_status check (status in ('QUEUED','RUNNING','COMPLETED','FAILED','CANCELLED','DEAD_LETTERED')),
    constraint ux_backfill_batches_run_sequence unique (run_id, sequence_no)
);
create index if not exists ix_backfill_batches_run_status on gmail_backfill_batches (run_id, status);
create index if not exists ix_backfill_batches_owner on gmail_backfill_batches (tenant_id, user_id, status);
