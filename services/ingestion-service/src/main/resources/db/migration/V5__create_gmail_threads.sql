create table if not exists gmail_threads (
    thread_id varchar(255) not null,
    connection_id uuid not null,
    tenant_id varchar(120) not null,
    user_id varchar(120) not null,
    first_seen_at timestamp with time zone not null,
    last_seen_at timestamp with time zone not null,
    primary key (connection_id, thread_id),
    constraint fk_gmail_threads_connection foreign key (connection_id) references gmail_connections(connection_id)
);

create index if not exists ix_gmail_threads_owner on gmail_threads (tenant_id, user_id);
