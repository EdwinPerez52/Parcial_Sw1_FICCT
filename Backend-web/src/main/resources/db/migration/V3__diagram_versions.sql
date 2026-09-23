create table diagram_versions (
    id uuid primary key,
    diagram_id uuid not null references diagrams(id) on delete cascade,
    source_revision bigint not null,
    label varchar(180) not null,
    snapshot_json text not null,
    author_subject varchar(255) not null,
    author_name varchar(255) not null,
    created_at timestamptz not null
);

create index idx_diagram_versions_created on diagram_versions(diagram_id, created_at desc);
