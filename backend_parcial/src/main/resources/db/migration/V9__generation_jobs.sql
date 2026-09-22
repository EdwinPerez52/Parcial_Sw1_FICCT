create table generation_jobs (
    id uuid primary key,
    diagram_id uuid not null references diagrams(id) on delete cascade,
    version_id uuid not null references diagram_versions(id) on delete restrict,
    source_revision bigint not null,
    requester_subject varchar(255) not null,
    requester_name varchar(255) not null,
    idempotency_key varchar(120) not null,
    status varchar(16) not null,
    attempt integer not null default 0,
    group_id varchar(180) not null,
    artifact_id varchar(180) not null,
    backend_object_key varchar(500),
    mobile_spec_object_key varchar(500),
    backend_filename varchar(255),
    mobile_spec_filename varchar(255),
    error_code varchar(80),
    error_message varchar(500),
    queued_at timestamptz not null,
    started_at timestamptz,
    completed_at timestamptz,
    expires_at timestamptz,
    constraint chk_generation_job_status check (status in ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    constraint uq_generation_job_idempotency unique (diagram_id, requester_subject, idempotency_key)
);

create index idx_generation_jobs_diagram_created on generation_jobs(diagram_id, queued_at desc);
create index idx_generation_jobs_status_queued on generation_jobs(status, queued_at);

