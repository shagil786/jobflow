create table if not exists gmail_application_promotion_outbox (
    outbox_id uuid primary key,
    tenant_id varchar(120) not null,
    user_id varchar(120) not null,
    batch_id uuid not null,
    suggestion_id varchar(255) not null,
    message_id varchar(255) not null,
    company varchar(255),
    role varchar(255) not null,
    intent varchar(64) not null,
    application_date varchar(32),
    status varchar(24) not null,
    attempts integer not null default 0,
    next_attempt_at timestamp with time zone not null,
    last_error varchar(512),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint ux_gmail_promotion_suggestion unique (suggestion_id),
    constraint fk_gmail_promotion_batch foreign key (batch_id) references gmail_backfill_batches(batch_id)
);
create index if not exists ix_gmail_promotion_due on gmail_application_promotion_outbox (status, next_attempt_at);
