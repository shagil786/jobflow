alter table classification_suggestions add column if not exists review_reasons_json varchar(4096) not null default '[]';
alter table gmail_backfill_batches add column if not exists bodies_fetched integer not null default 0;
alter table gmail_backfill_batches add column if not exists indexed_threads integer not null default 0;
alter table gmail_backfill_batches add column if not exists classified_threads integer not null default 0;
alter table gmail_backfill_batches add column if not exists auto_promoted integer not null default 0;
alter table gmail_backfill_batches add column if not exists needs_review integer not null default 0;
