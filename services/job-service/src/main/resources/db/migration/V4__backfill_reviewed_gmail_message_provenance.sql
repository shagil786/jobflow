update applications a
set source_message_id = (
    select substring(e.summary, 24)
    from timeline_events e
    where e.tenant_id = a.tenant_id
      and e.user_id = a.user_id
      and e.application_id = a.id
      and e.summary like 'Reviewed Gmail message %'
    order by e.occurred_at desc
    limit 1
)
where a.source = 'gmail-review'
  and a.source_message_id is null
  and exists (
    select 1 from timeline_events e
    where e.tenant_id = a.tenant_id
      and e.user_id = a.user_id
      and e.application_id = a.id
      and e.summary like 'Reviewed Gmail message %'
  );
