create table if not exists gmail_messages (
    message_id varchar(255) primary key,
    connection_id uuid not null,
    thread_id varchar(255) not null,
    internal_date timestamp with time zone,
    label_ids varchar(2048),
    captured_at timestamp with time zone not null,
    constraint fk_gmail_messages_connection foreign key (connection_id) references gmail_connections(connection_id)
);
create unique index if not exists ux_gmail_message_connection on gmail_messages (connection_id, message_id);
