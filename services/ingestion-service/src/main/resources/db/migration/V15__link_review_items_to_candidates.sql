alter table classification_reviews add column if not exists candidate_id uuid;
alter table classification_reviews add constraint fk_reviews_candidate foreign key (candidate_id) references gmail_message_candidates(candidate_id);
create unique index if not exists ux_reviews_candidate on classification_reviews (candidate_id);
create index if not exists ix_reviews_candidate_owner on classification_reviews (candidate_id, tenant_id, user_id);
