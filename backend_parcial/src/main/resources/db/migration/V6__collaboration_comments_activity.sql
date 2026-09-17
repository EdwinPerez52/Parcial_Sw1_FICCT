alter table comments add column parent_comment_id uuid references comments(id) on delete cascade;
alter table comments add column resolved_at timestamptz;
alter table comments add column resolved_by_subject varchar(255);

create index idx_comments_parent on comments(parent_comment_id, created_at);

create table diagram_activity (
    id uuid primary key,
    diagram_id uuid not null references diagrams(id) on delete cascade,
    event_type varchar(64) not null,
    summary varchar(500) not null,
    actor_subject varchar(255) not null,
    actor_name varchar(255) not null,
    element_id uuid,
    created_at timestamptz not null,
    constraint chk_activity_event_type check (length(trim(event_type)) > 0),
    constraint chk_activity_summary check (length(trim(summary)) > 0)
);

create index idx_activity_diagram_created on diagram_activity(diagram_id, created_at desc);
