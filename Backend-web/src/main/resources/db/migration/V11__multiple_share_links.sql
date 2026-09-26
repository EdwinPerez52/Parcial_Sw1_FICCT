create table diagram_share_links (
    id uuid primary key,
    diagram_id uuid not null references diagrams(id) on delete cascade,
    token_hash varchar(255) not null unique,
    created_at timestamptz not null,
    revoked_at timestamptz
);

create index idx_diagram_share_links_active
    on diagram_share_links(diagram_id)
    where revoked_at is null;
