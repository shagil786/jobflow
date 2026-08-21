create table if not exists classification_reviews (
    review_id uuid primary key,
    suggestion_id varchar(36) not null,
    tenant_id varchar(120) not null,
    user_id varchar(120) not null,
    decision varchar(16) not null,
    company varchar(240),
    role varchar(240),
    application_date varchar(64),
    contact varchar(320),
    reviewed_at timestamp with time zone not null default current_timestamp,
    constraint ux_classification_reviews_suggestion unique (suggestion_id),
    constraint ck_classification_reviews_decision check (decision in ('ACCEPT','CORRECT','DISMISS'))
);
create index if not exists ix_classification_reviews_owner on classification_reviews (tenant_id, user_id, reviewed_at);
