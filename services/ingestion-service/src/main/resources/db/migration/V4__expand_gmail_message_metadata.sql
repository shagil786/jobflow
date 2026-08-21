create table gmail_messages_v2 (
    connection_id uuid not null,
    message_id varchar(255) not null,
    thread_id varchar(255) not null,
    tenant_id varchar(120),
    user_id varchar(120),
    sender varchar(320),
    reply_to varchar(320),
    recipients varchar(2048),
    subject varchar(1024),
    received_at timestamp with time zone,
    label_ids varchar(2048),
    normalized_content_hash varchar(128),
    captured_at timestamp with time zone not null,
    primary key (connection_id, message_id),
    constraint fk_gmail_messages_v2_connection foreign key (connection_id) references gmail_connections(connection_id)
);

insert into gmail_messages_v2 (
    connection_id,
    message_id,
    thread_id,
    tenant_id,
    user_id,
    sender,
    reply_to,
    recipients,
    subject,
    received_at,
    label_ids,
    normalized_content_hash,
    captured_at
)
select
    messages.connection_id,
    messages.message_id,
    messages.thread_id,
    connections.tenant_id,
    connections.user_id,
    null,
    null,
    null,
    null,
    messages.internal_date,
    messages.label_ids,
    null,
    messages.captured_at
from gmail_messages messages
join gmail_connections connections on connections.connection_id = messages.connection_id;

drop table gmail_messages;
alter table gmail_messages_v2 rename to gmail_messages;

create index if not exists ix_gmail_messages_owner on gmail_messages (tenant_id, user_id, connection_id);
