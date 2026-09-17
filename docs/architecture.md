# Arquitectura

## Componentes

- `frontend_parcial`: React, TypeScript, React Flow y Zustand. Mantiene una vista optimista inmutable y una outbox persistente.
- `backend_parcial`: Spring Boot. Autoriza, valida operaciones, persiste el modelo y genera artefactos.
- PostgreSQL: instantánea actual, miembros, comentarios, conversaciones, actividad, versiones e historial de operaciones.
- Redis: presencia efímera con expiración y fan-out de eventos entre réplicas.
- S3: reservado para imágenes y artefactos generados en AWS.

## Flujo de colaboración

1. El cliente carga la instantánea y su revisión confirmada.
2. Cada acción se convierte en una `DiagramOperation` con UUID, revisión base y versión esperada.
3. El cliente aplica el cambio de forma optimista y lo guarda en la outbox.
4. La API bloquea la fila del diagrama, comprueba idempotencia y versión del elemento.
5. Cambios sobre elementos independientes pueden partir de una revisión antigua y converger.
6. La API registra una revisión nueva, publica el evento en Redis y cada réplica lo difunde por `/topic/diagrams/{id}`.
7. Un conflicto responde 409; el cliente conserva las variantes local y remota y permite descartar, reintentar o reaplicar.
8. Tras reconectar, el cliente solicita `/operations?since=revision`, aplica las operaciones en orden y reenvía la outbox con los mismos `operationId`.

Los `BATCH` se validan completos y se confirman en una única transacción. Consulta [uml-json-contract.md](./uml-json-contract.md) y [collaboration.md](./collaboration.md).

## Persistencia

Flyway es la única fuente del esquema y Hibernate usa `ddl-auto=validate`. Las claves foráneas del agregado eliminan en cascada, y existen índices por revisión, sujeto, rol y fecha. Los enlaces compartidos se guardan únicamente como SHA-256; el token en claro solo se entrega al rotarlo.

- `dev`: PostgreSQL y Redis locales reales.
- `test`: PostgreSQL Testcontainers; el fan-out Redis se desactiva para pruebas unitarias aisladas.
- `prod`: conexiones mediante variables obligatorias y orígenes WebSocket explícitos.

## Seguridad

- Producción exige OAuth 2.0/OpenID Connect con Google.
- Los enlaces compartidos contienen un token aleatorio y la base solo conserva su hash.
- La importación XML deshabilita DTD y entidades externas.
- El handshake WebSocket requiere sesión autenticada. Un interceptor valida membresía para cada `SUBSCRIBE` y `SEND`.
- Los orígenes WebSocket se definen con `WEBSOCKET_ALLOWED_ORIGINS`; producción no admite comodines.

## Presencia, historial y conversaciones

- Cursores, selección, heartbeat y sesiones activas viven en Redis con TTL; no se escriben movimientos en PostgreSQL.
- Comentarios, respuestas, resolución y actividad relevante sí se guardan en PostgreSQL y se difunden en tiempo real.
- Deshacer y rehacer generan operaciones compensatorias. Los hitos son instantáneas persistentes independientes.
- Restaurar un hito exige la revisión esperada, crea una revisión nueva y conserva operaciones e hitos previos.

## Límites actuales

- Cada réplica mantiene un broker STOMP local; Redis distribuye eventos entre réplicas.
- XMI conserva el modelo semántico, no el diseño propietario de Enterprise Architect.
- La regeneración produce un ZIP nuevo y no mezcla código editado manualmente.
- La aplicación Flutter y su generador se implementan en los incrementos 16–19.
