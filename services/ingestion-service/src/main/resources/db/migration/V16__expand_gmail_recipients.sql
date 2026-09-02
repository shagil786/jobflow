-- Gmail recipient headers can contain long distribution lists and quoted names.
-- Keep the complete normalized header so ingestion never drops a real message.
alter table gmail_messages alter column recipients varchar(16384);
