alter table applications add column if not exists source_thread_id varchar(255);
alter table applications add column if not exists source_message_id varchar(255);
alter table applications add column if not exists source_direction varchar(16);
create index if not exists idx_applications_source_thread on applications (tenant_id, user_id, source_thread_id);
