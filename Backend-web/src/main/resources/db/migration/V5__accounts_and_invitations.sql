create table user_accounts (
    id uuid primary key,
    email varchar(320) not null,
    full_name varchar(180) not null,
    email_verified boolean not null default false,
    platform_admin boolean not null default false,
    failed_login_attempts integer not null default 0,
    locked_until timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_user_accounts_email unique (email),
    constraint chk_user_accounts_email_normalized check (email = lower(trim(email))),
    constraint chk_user_accounts_name_not_blank check (length(trim(full_name)) > 0)
);

create table auth_identities (
    id uuid primary key,
    account_id uuid not null references user_accounts(id) on delete cascade,
    provider varchar(24) not null,
    provider_subject varchar(320) not null,
    password_hash varchar(255),
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_auth_identity_provider_subject unique (provider, provider_subject),
    constraint uq_auth_identity_account_provider unique (account_id, provider),
    constraint chk_auth_identity_provider check (provider in ('LOCAL', 'GOOGLE')),
    constraint chk_auth_identity_password check (
        (provider = 'LOCAL' and password_hash is not null) or
        (provider = 'GOOGLE' and password_hash is null)
    )
);

create table invitations (
    id uuid primary key,
    token_hash varchar(255) not null unique,
    email varchar(320),
    diagram_id uuid references diagrams(id) on delete cascade,
    bootstrap_admin boolean not null default false,
    expires_at timestamptz not null,
    accepted_by_account_id uuid references user_accounts(id),
    used_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz not null,
    constraint chk_invitation_destination check (email is not null or diagram_id is not null)
);

create table email_verification_tokens (
    id uuid primary key,
    account_id uuid not null references user_accounts(id) on delete cascade,
    token_hash varchar(255) not null unique,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null
);

create table password_reset_tokens (
    id uuid primary key,
    account_id uuid not null references user_accounts(id) on delete cascade,
    token_hash varchar(255) not null unique,
    expires_at timestamptz not null,
    used_at timestamptz,
    created_at timestamptz not null
);

create table pending_diagram_joins (
    account_id uuid not null references user_accounts(id) on delete cascade,
    diagram_id uuid not null references diagrams(id) on delete cascade,
    created_at timestamptz not null,
    primary key (account_id, diagram_id)
);

alter table diagram_members add column account_id uuid references user_accounts(id);
alter table diagrams add column owner_account_id uuid references user_accounts(id);

create index idx_auth_identities_account on auth_identities(account_id);
create index idx_invitations_email on invitations(email);
create index idx_verification_account on email_verification_tokens(account_id);
create index idx_password_reset_account on password_reset_tokens(account_id);
create index idx_members_account on diagram_members(account_id);
