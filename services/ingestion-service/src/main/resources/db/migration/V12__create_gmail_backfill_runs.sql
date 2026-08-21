create table if not exists gmail_backfill_runs (
    run_id uuid primary key,
    tenant_id varchar(120) not null,
    user_id varchar(120) not null,
    connection_id uuid not null,
    idempotency_key varchar(255) not null,
    mode varchar(32) not null,
    status varchar(32) not null,
    requested_from timestamp with time zone not null,
    requested_to timestamp with time zone not null,
    batch_size_days integer not null,
    total_batches integer not null default 0,
    completed_batches integer not null default 0,
    failed_batches integer not null default 0,
    imported_messages integer not null default 0,
    correlation_id varchar(255) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    active_owner_key varchar(120) not null,
    constraint fk_backfill_runs_connection foreign key (connection_id) references gmail_connections(connection_id),
    constraint ck_backfill_runs_range check (requested_to > requested_from),
    constraint ck_backfill_runs_mode check (mode in ('AUTOMATIC', 'LABEL_SCOPED')),
    constraint ck_backfill_runs_status check (status in ('QUEUED','RUNNING','PAUSING','PAUSED','CANCELLING','CANCELLED','COMPLETED','FAILED'))
);
create unique index if not exists ux_backfill_runs_idempotency on gmail_backfill_runs (tenant_id, user_id, idempotency_key);
-- The active slot is the portable equivalent of a PostgreSQL partial index.
create unique index if not exists ux_backfill_runs_active_owner on gmail_backfill_runs (tenant_id, user_id, active_owner_key);
create index if not exists ix_backfill_runs_owner_status on gmail_backfill_runs (tenant_id, user_id, status);
create index if not exists ix_backfill_runs_connection_status on gmail_backfill_runs (connection_id, status);
