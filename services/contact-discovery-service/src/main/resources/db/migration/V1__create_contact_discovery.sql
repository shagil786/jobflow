create table contact_enrichment_runs (
  id uuid primary key,
  application_id uuid not null,
  tenant_id varchar(160) not null,
  user_id varchar(160) not null,
  status varchar(32) not null,
  idempotency_key varchar(160) not null,
  request_hash varchar(128) not null,
  discovered_contacts integer not null default 0,
  verified_contacts integer not null default 0,
  created_at timestamp with time zone not null,
  completed_at timestamp with time zone,
  constraint uq_contact_enrichment_owner_key unique (tenant_id, user_id, application_id, idempotency_key)
);

create table contact_candidates (
  id uuid primary key,
  application_id uuid not null,
  tenant_id varchar(160) not null,
  user_id varchar(160) not null,
  name varchar(240),
  contact_role varchar(240),
  company varchar(240),
  email varchar(320),
  profile_url varchar(1000),
  source_url varchar(1000),
  source_type varchar(32) not null,
  evidence varchar(2000) not null,
  source_message_id varchar(240),
  source_thread_id varchar(240),
  confidence double not null,
  status varchar(32) not null,
  verification_status varchar(32) not null,
  provider varchar(120) not null,
  provider_version varchar(80) not null,
  allowed_use varchar(32) not null,
  selected boolean not null default false,
  content_hash varchar(128) not null,
  created_at timestamp with time zone not null,
  expires_at timestamp with time zone,
  constraint uq_contact_candidate_content unique (tenant_id, user_id, application_id, content_hash)
);
create index ix_contact_candidates_owner_application on contact_candidates (tenant_id, user_id, application_id, created_at);
