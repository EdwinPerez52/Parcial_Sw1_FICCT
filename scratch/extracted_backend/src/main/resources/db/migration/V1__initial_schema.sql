-- Isolated Authentication Schema
create table _app_auth_users (
    id uuid primary key,
    email varchar(255) not null unique,
    password_hash varchar(255) not null,
    full_name varchar(255) not null,
    role varchar(50) not null,
    created_at timestamp with time zone not null
);

create table _app_auth_tokens (
    id uuid primary key,
    user_id uuid not null references _app_auth_users(id) on delete cascade,
    token varchar(512) not null unique,
    expires_at timestamp with time zone not null,
    revoked boolean not null default false,
    created_at timestamp with time zone not null
);

create index idx_auth_users_email on _app_auth_users(email);
create index idx_auth_tokens_token on _app_auth_tokens(token);

create table usuario (
    id integer primary key unique,
    nombre varchar(255),
    correo varchar(255)
);

create table doctor (
    id uuid primary key not null unique,
    profesion varchar(255),
    usuario_id integer
);

create table prubea (
    id uuid primary key not null unique,
    h varchar(255),
    sgsg varchar(255),
    usuario_id integer
);

create table producto (
    id uuid primary key not null unique,
    precio numeric(19,2),
    usuario_id integer
);

-- Foreign Key Constraints
alter table doctor add constraint fk_doctor_usuario_id foreign key (usuario_id) references usuario(id);
alter table prubea add constraint fk_prubea_usuario_id foreign key (usuario_id) references usuario(id);
alter table producto add constraint fk_producto_usuario_id foreign key (usuario_id) references usuario(id);

