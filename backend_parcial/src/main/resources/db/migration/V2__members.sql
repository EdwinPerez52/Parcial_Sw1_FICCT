create table diagram_members (
    id uuid primary key,
    diagram_id uuid not null references diagrams(id) on delete cascade,
    subject varchar(255) not null,
    display_name varchar(255) not null,
    role varchar(24) not null,
    joined_at timestamptz not null,
    unique (diagram_id, subject)
);

create index idx_diagram_members_subject on diagram_members(subject);
create unique index idx_diagrams_share_token on diagrams(share_token_hash) where share_token_hash is not null;
