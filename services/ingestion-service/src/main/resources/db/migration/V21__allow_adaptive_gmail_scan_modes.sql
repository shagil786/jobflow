alter table gmail_backfill_runs drop constraint if exists ck_backfill_runs_mode;

alter table gmail_backfill_runs
    add constraint ck_backfill_runs_mode
        check (mode in ('FOCUSED', 'BROAD', 'FULL', 'AUTOMATIC', 'LABEL_SCOPED'));
