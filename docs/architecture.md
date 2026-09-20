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

## Asistente de texto

1. `POST /api/v1/diagrams/{id}/assistant/proposals` exige rol editor y recibe solo la instrucción.
2. `LocalCommandParser` resuelve comandos simples de creación, modificación, movimiento, relaciones, enumeraciones, herencia y eliminación.
3. Si el parser no reconoce la instrucción, el adaptador `TextCommandProvider` configurado recibe el contexto mínimo del diagrama y debe devolver una única `DiagramOperation` o `BATCH` bajo un esquema JSON estricto.
   Para diseños de dominio breves, el adaptador puede solicitar operaciones atómicas adicionales hasta reunir entre 4 y 8 clases y al menos 3 asociaciones válidas; remapea todos los identificadores nuevos a UUID generados localmente y conserva sus referencias cruzadas.
4. `DiagramService.preview` aplica la operación sobre una copia y ejecuta todas las validaciones de dominio, sin persistir ni publicar eventos.
5. La propuesta temporal conserva operación, autor, proveedor y SHA-256 de la instrucción; no conserva el texto natural ni credenciales.
6. Las eliminaciones y lotes de más de cinco operaciones requieren confirmación. Al aplicar, se bloquean propuesta y diagrama, se evita el doble uso y se llama al mismo `DiagramService.apply` del editor manual.

El proveedor nunca recibe acceso a PostgreSQL ni produce SQL/código ejecutable. Los alias SQL comunes de tipos y cardinalidades se convierten al vocabulario cerrado del modelo antes de la previsualización. Una salida inválida termina antes de crear la propuesta y, por tanto, no cambia revisión, instantánea ni historial.

La web graba audio con MediaRecorder y lo envía a `POST /api/v1/diagrams/{id}/assistant/transcriptions` (editor verificado, 5 MB, MIME inspeccionado); el backend usa el adaptador de transcripción configurado y devuelve solo texto. Si el proveedor no está disponible se intenta el reconocimiento del navegador. Ese texto se envía a `POST /api/v1/diagrams/{id}/assistant/proposals`; no existe una ruta de escritura alternativa. La fotografía usa `POST /api/v1/diagrams/{id}/image-preview`, exige editor verificado, valida bytes, MIME y dimensiones, y solicita una propuesta estructurada al adaptador de visión. La previsualización editable vive en el cliente. Solo al confirmar se genera una transición `BATCH` mediante el servicio de operaciones versionadas; cancelar no genera operaciones. El endpoint anterior `/api/v1/ai/image-preview` se conserva para compatibilidad de clientes autenticados.

## Persistencia

Flyway es la única fuente del esquema y Hibernate usa `ddl-auto=validate`. Las claves foráneas del agregado eliminan en cascada, y existen índices por revisión, sujeto, rol y fecha. Los enlaces compartidos se guardan únicamente como SHA-256; el token en claro solo se entrega al rotarlo.

- `dev`: PostgreSQL y Redis locales reales.
- `test`: PostgreSQL Testcontainers; el fan-out Redis se desactiva para pruebas unitarias aisladas.
- `prod`: conexiones mediante variables obligatorias y orígenes WebSocket explícitos.

## Seguridad

- Autenticación segura mediante contraseñas BCrypt, tokens de acceso y sesión con cookies HTTP seguras.
- Los enlaces compartidos contienen un token aleatorio y la base solo conserva su hash.
- La importación XML deshabilita DTD y entidades externas.
- El handshake WebSocket requiere sesión autenticada. Un interceptor valida membresía para cada `SUBSCRIBE` y `SEND`.
- Los orígenes WebSocket se definen con `WEBSOCKET_ALLOWED_ORIGINS`; producción no admite comodines.

## Presencia, historial y conversaciones

- Cursores, selección, heartbeat y sesiones activas viven en Redis con TTL; no se escriben movimientos en PostgreSQL.
- Comentarios, respuestas, resolución y actividad relevante sí se guardan en PostgreSQL y se difunden en tiempo real.
- Deshacer y rehacer generan operaciones compensatorias. Los hitos son instantáneas persistentes independientes.
- Restaurar un hito exige la revisión esperada, crea una revisión nueva y conserva operaciones e hitos previos.

## Generador Spring Boot y Especificación Móvil

- **Validación previa estricta**: el validador inspecciona identificadores, tipos escalares y enumerados, claves primarias únicas, referencias válidas, cardinalidades UML y ciclos de herencia. Cualquier inconsistencia emite `ModelValidationException` con el UUID exacto del elemento infractor.
- **Generación desde versiones inmutables**: la generación del backend y de la especificación móvil se basa en hitos o versiones persistidas (`DiagramVersionEntity`), garantizando consistencia determinista entre el código generado y la revisión del modelo.
- **Arquitectura del backend generado**:
  - Java 21 y Spring Boot 3.5.x.
  - Entidades JPA con herencia `JOINED`, relaciones 1:1, 1:N y N:M con lado propietario (`owningSide`) explícito, y enumeraciones mapeadas como `STRING`.
  - DTOs separados para entrada y salida con validación Bean Validation (`@NotNull`, `@NotBlank`, `@Size`).
  - Mapeadores, repositorios Spring Data JPA, servicios transaccionales y controladores REST con CRUD completo y actualización.
  - Manejo uniforme de errores con `ProblemDetail` / `ErrorResponse` y contrato OpenAPI 3.0.3 en `openapi.yaml`.
  - Autenticación completa y aislada: tablas de credenciales (`_app_auth_users`) y tokens (`_app_auth_tokens`), contraseñas BCrypt, tokens JWT de acceso y refresco, asignación de rol `ADMIN` al primer usuario registrado y `USER` a los subsiguientes.
  - Migración Flyway inicial con resolución ordenada de claves foráneas mediante `ALTER TABLE` para evitar dependencias circulares en DDL.
  - `Dockerfile` multi-stage, `docker-compose.yml`, `README.md` y pruebas de integración con Testcontainers.
- **Trazabilidad del modelo**:
  - Anotación `@ModelElement` en clases, atributos y relaciones.
  - Archivo `model-traceability.json` con rutas de archivo y números de línea (1-indexed) de cada elemento generado.
- **Especificación móvil firmada**:
  - Generación de `modeler-mobile-spec.json` firmado con HMAC-SHA256, vinculado al hash SHA-256 del `openapi.yaml` y la revisión del modelo.
  - Permite al generador móvil Flutter verificar la integridad del contrato antes de generar los clientes locales y pantallas CRUD.
- **Protección contra path traversal**:
  - Control de nombres de entrada en los archivos ZIP generados para evitar secuencias `..` o rutas absolutas maliciosas.

## Límites actuales

- Cada réplica mantiene un broker STOMP local; Redis distribuye eventos entre réplicas.
- XMI conserva el modelo semántico, no el diseño propietario de Enterprise Architect.
- La vista previa XMI es de solo lectura. La confirmación se traduce en un único `BATCH` del mismo flujo colaborativo; no existe una escritura lateral que evite revisiones, autorización, conflictos o deshacer.
- La regeneración produce un ZIP nuevo y no mezcla código editado manualmente.
- La aplicación Flutter y su generador se implementan en los incrementos 16–19; aún no existe una aplicación Flutter en este repositorio a la que conectar estas entradas.

