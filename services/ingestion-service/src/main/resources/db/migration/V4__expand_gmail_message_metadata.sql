alter table gmail_messages add column if not exists tenant_id varchar(120);
alter table gmail_messages add column if not exists user_id varchar(120);
alter table gmail_messages add column if not exists sender varchar(320);
alter table gmail_messages add column if not exists reply_to varchar(320);
alter table gmail_messages add column if not exists recipients varchar(2048);
alter table gmail_messages add column if not exists subject varchar(1024);
alter table gmail_messages add column if not exists received_at timestamp with time zone;
alter table gmail_messages add column if not exists normalized_content_hash varchar(128);

update gmail_messages
set tenant_id = (select tenant.tenant_id from gmail_connections tenant where tenant.connection_id = gmail_messages.connection_id),
    user_id = (select tenant.user_id from gmail_connections tenant where tenant.connection_id = gmail_messages.connection_id),
    received_at = internal_date
where connection_id in (select connection_id from gmail_connections);

alter table gmail_messages alter column tenant_id set not null;
alter table gmail_messages alter column user_id set not null;

alter table gmail_messages drop constraint if exists gmail_messages_pkey;
alter table gmail_messages drop constraint if exists constraint_6;
alter table gmail_messages add constraint pk_gmail_messages_identity primary key (connection_id, message_id);

create index if not exists ix_gmail_messages_owner on gmail_messages (tenant_id, user_id, connection_id);
