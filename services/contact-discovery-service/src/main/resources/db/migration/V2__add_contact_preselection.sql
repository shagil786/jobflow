alter table contact_candidates add column preselected boolean not null default false;
create index ix_contact_candidates_preselected on contact_candidates (tenant_id, user_id, application_id, preselected);
