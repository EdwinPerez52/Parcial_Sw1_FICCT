# Modelo de datos persistente

Flyway es la única fuente de verdad del esquema. Hibernate solo valida (`ddl-auto=validate`) y las migraciones `V1` a `V10` son inmutables. Una modificación futura debe añadirse como `V11__descripcion.sql`, nunca editar una migración aplicada.

| Agregado | Tablas | Propósito e integridad |
| --- | --- | --- |
| Diagrama | `diagrams`, `diagram_members`, `diagram_operations`, `diagram_versions` | La instantánea JSON y su revisión son autoritativas. `diagram_operations.id` es idempotente; las versiones son snapshots inmutables. Miembros tienen rol por diagrama. |
| Identidad | `user_accounts`, `auth_identities`, `invitations`, `email_verification_tokens`, `password_reset_tokens`, `pending_diagram_joins` | Una cuenta tiene correo normalizado único y una identidad local con contraseña. Los tokens y enlaces se almacenan como hashes; los tokens de un uso conservan expiración y consumo. |
| Conversación | `comments`, `diagram_activity` | Comentarios anclados admiten hilos, estado resuelto y bloqueo optimista. Actividad guarda solo eventos relevantes, no presencia. |
| Asistente | `assistant_proposals` | Propuesta temporal con operación validada, autor, proveedor y hash de la instrucción; no conserva el texto original. |
| Generación | `generation_jobs` | Solicitud durable ligada a `diagram_versions`; la clave única de idempotencia evita ZIP/spec duplicados. Las claves de objetos se publican solo tras `SUCCEEDED`. |

## Datos efímeros y retención

Redis conserva presencia, cursor, selección y fan-out; no almacena historial ni credenciales. Los artefactos locales se cifran y los de AWS se guardan en S3; `GENERATION_RETENTION_HOURS` controla su vencimiento. Los backups de RDS se gestionan por Terraform y su restauración se describe en [release-validation.md](./release-validation.md).

## Relaciones críticas

- `user_accounts` 1:1 `auth_identities`; la única identidad admitida es `LOCAL` y mantiene el hash de contraseña.
- `diagrams` 1:N miembros, operaciones, comentarios, versiones y jobs; sus claves foráneas eliminan el agregado de forma controlada.
- `generation_jobs.version_id` usa `RESTRICT`: un artefacto nunca pierde la revisión que le da trazabilidad.

La semántica completa de `DiagramModel`, su `revision` y las versiones de elementos se especifica en [uml-json-contract.md](./uml-json-contract.md).
