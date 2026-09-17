alter table diagrams
    add constraint chk_diagrams_revision_nonnegative check (revision >= 0),
    add constraint chk_diagrams_name_not_blank check (length(trim(name)) > 0);

alter table diagram_operations
    add constraint chk_operations_revisions check (base_revision >= 0 and result_revision > 0),
    add constraint chk_operations_type_not_blank check (length(trim(type)) > 0);

alter table diagram_members
    add constraint chk_members_role check (role in ('OWNER', 'EDITOR', 'READER', 'PENDING'));

alter table comments
    add constraint chk_comments_target check (
        target_type in ('DIAGRAM', 'CLASS', 'ATTRIBUTE', 'ASSOCIATION', 'ENUMERATION', 'GENERALIZATION')
        and ((target_type = 'DIAGRAM' and target_id is null) or (target_type <> 'DIAGRAM' and target_id is not null))
    ),
    add constraint chk_comments_body_not_blank check (length(trim(body)) > 0);

alter table diagram_versions
    add constraint chk_versions_revision_nonnegative check (source_revision >= 0),
    add constraint chk_versions_label_not_blank check (length(trim(label)) > 0);

create index idx_comments_diagram_created on comments(diagram_id, created_at);
create index idx_versions_diagram_revision on diagram_versions(diagram_id, source_revision);
create index idx_members_diagram_role on diagram_members(diagram_id, role);
