drop index if exists ix_classification_suggestions_latest;

create index if not exists ix_classification_suggestions_latest
    on classification_suggestions (tenant_id, user_id, connection_id, message_id, row_id);
