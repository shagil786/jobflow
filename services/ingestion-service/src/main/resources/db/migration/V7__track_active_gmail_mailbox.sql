alter table gmail_connections add column if not exists active boolean not null default false;

update gmail_connections connection
set active = true
where connection.connection_id in (
    select ranked.connection_id
    from (
        select connection_id,
               row_number() over (
                   partition by tenant_id, user_id
                   order by connected_at desc, connection_id desc
               ) as rank
        from gmail_connections
    ) ranked
    where ranked.rank = 1
);
