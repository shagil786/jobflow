create table if not exists gmail_connection_owner_locks (
    tenant_id varchar(120) not null,
    user_id varchar(120) not null,
    primary key (tenant_id, user_id)
);

insert into gmail_connection_owner_locks (tenant_id, user_id)
select distinct tenant_id, user_id from gmail_connections;
