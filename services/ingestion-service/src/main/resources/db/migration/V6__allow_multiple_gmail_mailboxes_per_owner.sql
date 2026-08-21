update gmail_connections set email = lower(email);

drop index if exists ux_gmail_connection_owner;
create unique index if not exists ux_gmail_connection_mailbox
    on gmail_connections (tenant_id, user_id, email);
