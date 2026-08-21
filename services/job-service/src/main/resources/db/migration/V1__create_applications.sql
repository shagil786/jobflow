create table if not exists applications (
    id uuid primary key,
    tenant_id varchar(120) not null,
    user_id varchar(120) not null,
    company varchar(240) not null,
    role varchar(240) not null,
    job_url varchar(2048),
    capture_title varchar(240) not null,
    description_preview varchar(2000),
    captured_at timestamp with time zone not null,
    source varchar(80),
    location varchar(240),
    status varchar(32) not null,
    applied_date date,
    next_follow_up_date date,
    idempotency_key varchar(240) not null,
    idempotency_payload_hash varchar(64),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null
);

create unique index if not exists ux_capture_idempotency on applications (tenant_id, user_id, idempotency_key);
create index if not exists idx_applications_owner_updated on applications (tenant_id, user_id, updated_at desc, id);

create table if not exists timeline_events (
    id uuid primary key,
    tenant_id varchar(120) not null,
    user_id varchar(120) not null,
    application_id uuid not null,
    type varchar(80) not null,
    summary varchar(2000) not null,
    occurred_at timestamp with time zone not null
);

create index if not exists idx_timeline_application_occurred on timeline_events (application_id, occurred_at desc, id);
