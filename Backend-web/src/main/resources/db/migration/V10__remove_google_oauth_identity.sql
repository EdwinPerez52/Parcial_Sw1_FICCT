-- La autenticación de la plataforma es exclusivamente local (correo y contraseña).
delete from auth_identities where provider = 'GOOGLE';
alter table auth_identities drop constraint if exists chk_auth_identity_provider;
alter table auth_identities drop constraint if exists chk_auth_identity_password;
alter table auth_identities add constraint chk_auth_identity_provider check (provider = 'LOCAL');
alter table auth_identities add constraint chk_auth_identity_password check (password_hash is not null);
