create table if not exists gmail_connections (
    connection_id uuid primary key,
    user_id varchar(120) not null,
    tenant_id varchar(120) not null,
    email varchar(320) not null,
    refresh_token_ciphertext text not null,
    last_history_id varchar(255) not null,
    page_token varchar(255),
    connected_at timestamp with time zone not null
);
create unique index if not exists ux_gmail_connection_owner on gmail_connections (tenant_id, user_id);
