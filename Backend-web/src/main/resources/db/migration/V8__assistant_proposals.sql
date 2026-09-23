alter table diagram_operations
    add column source varchar(24) not null default 'MANUAL',
    add column ai_provider varchar(80),
    add constraint chk_diagram_operation_source check (source in ('MANUAL', 'ASSISTANT')),
    add constraint chk_assistant_provider check (
        (source = 'MANUAL' and ai_provider is null) or
        (source = 'ASSISTANT' and ai_provider is not null and length(trim(ai_provider)) > 0)
    );

create table assistant_proposals (
    id uuid primary key,
    diagram_id uuid not null references diagrams(id) on delete cascade,
    author_subject varchar(255) not null,
    provider varchar(80) not null,
    instruction_hash varchar(64) not null,
    operation_json text not null,
    requires_confirmation boolean not null,
    created_at timestamptz not null,
    expires_at timestamptz not null,
    applied_at timestamptz,
    constraint chk_assistant_proposal_provider check (length(trim(provider)) > 0),
    constraint chk_assistant_proposal_expiry check (expires_at > created_at)
);

create index idx_assistant_proposals_diagram on assistant_proposals(diagram_id, created_at desc);
