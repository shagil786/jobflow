create table if not exists sessions (
    session_id varchar(64) primary key,
    user_id varchar(255) not null,
    tenant_id varchar(255) not null,
    provider varchar(64) not null,
    access_token_ciphertext text not null,
    refresh_token_ciphertext text,
    access_token_expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone
);

create index if not exists idx_sessions_user_id on sessions (user_id);
create index if not exists idx_sessions_revoked_at on sessions (revoked_at);
