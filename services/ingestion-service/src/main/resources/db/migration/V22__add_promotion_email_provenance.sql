alter table gmail_application_promotion_outbox add column if not exists thread_id varchar(255);
alter table gmail_application_promotion_outbox add column if not exists direction varchar(16);
