create table diagrams (
    id uuid primary key,
    name varchar(180) not null,
    revision bigint not null default 0,
    model_json text not null,
    owner_subject varchar(255) not null,
    share_token_hash varchar(255),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table diagram_operations (
    id uuid primary key,
    diagram_id uuid not null references diagrams(id) on delete cascade,
    base_revision bigint not null,
    result_revision bigint not null,
    type varchar(80) not null,
    payload_json text not null,
    author_subject varchar(255) not null,
    author_name varchar(255) not null,
    created_at timestamptz not null,
    unique (diagram_id, result_revision)
);

create index idx_diagram_operations_revision on diagram_operations(diagram_id, result_revision);

create table comments (
    id uuid primary key,
    diagram_id uuid not null references diagrams(id) on delete cascade,
    target_type varchar(32) not null,
    target_id uuid,
    body text not null,
    author_subject varchar(255) not null,
    author_name varchar(255) not null,
    resolved boolean not null default false,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

