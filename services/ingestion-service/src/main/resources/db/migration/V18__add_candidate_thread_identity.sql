alter table gmail_message_candidates add column if not exists thread_id varchar(255);
update gmail_message_candidates c set thread_id = (select m.thread_id from gmail_messages m where m.connection_id = c.connection_id and m.message_id = c.provider_message_id) where c.thread_id is null;
create index if not exists ix_gmail_candidates_thread on gmail_message_candidates (connection_id, thread_id);
