# Arquitectura

## Componentes

- `Frontend-web`: React, TypeScript, React Flow y Zustand. Mantiene una vista optimista inmutable y una outbox persistente.
- `Backend-web`: Spring Boot. Autoriza, valida operaciones, persiste el modelo y genera artefactos.
- PostgreSQL: instantánea actual, miembros, comentarios, conversaciones, actividad, versiones e historial de operaciones.
- Redis: presencia efímera con expiración y fan-out de eventos entre réplicas.
- S3: reservado para imágenes y artefactos generados en AWS.

## Entrega y operación AWS

La topología Terraform contiene dos estados aislados (`test` y `prod`) sobre un bootstrap separado que conserva estado versionado, bloqueo y ECR. CloudFront sirve el SPA desde un bucket S3 privado con OAC y envía `/api/*`, `/ws*` y la comprobación de salud al ALB. API y worker son servicios ECS Fargate privados, con circuit breaker y rollback del despliegue; el ALB es su único ingreso. RDS, Redis y tareas permanecen en subredes privadas. Las credenciales de RDS las administra RDS en Secrets Manager y el resto de secretos de aplicación se inyectan desde un secreto distinto; nunca forman parte del `task definition`.

Las políticas IAM separan el rol de ejecución ECS (imágenes, logs y lectura de secretos) del rol de aplicación (S3/KMS de artefactos y SQS). SQS usa DLQ, los artefactos expiran, RDS cifra almacenamiento y conserva backups, CloudWatch conserva logs y SES recibe identidad de dominio, DKIM y `MAIL FROM` por Route 53. HTTPS termina en CloudFront y, cuando se configura dominio, también en ALB con ACM y el origen CloudFront usa HTTPS.

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

### Trabajos asíncronos de generación

- `generation_jobs` es el registro durable de la solicitud: revisión inmutable, solicitante, clave de idempotencia, intento, estado, error seguro y fechas. La clave única `(diagram_id, requester_subject, idempotency_key)` evita trabajos duplicados por reintentos de red.
- La API crea/consulta/reintenta trabajos bajo `/api/v1/diagrams/{diagramId}/generation-jobs`. Los artefactos solo se publican al pasar a `SUCCEEDED`; los endpoints verifican membresía además de una firma HMAC con expiración corta.
- En local, un listener `AFTER_COMMIT` ejecuta el worker sin depender del navegador y `EncryptedLocalArtifactStorage` conserva los bytes cifrados con AES-GCM. En producción, el evento se publica a SQS, un servicio ECS worker separado reclama el trabajo y `S3ArtifactStorage` usa cifrado SSE-S3 y claves aleatorias.
- Un trabajo emite exactamente un backend ZIP y un `modeler-mobile-spec.json` firmado desde el mismo `versionId`. El agente local recibe una variante con nonce de un solo uso y materializa Flutter en la computadora; nunca se almacena Flutter dentro del ZIP.

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
- La aplicación Flutter (`mobile-flutter`) y su generador (`FlutterGenerator`) implementan soporte completo para generación desde diagramas, autenticación con persistencia de tokens, almacenamiento offline SQLite, cola transaccional (outbox) y sincronización bidireccional con resolución visual de conflictos.

## Agente Local (Collab Modeler Local Agent)

El agente local es un servidor Node.js/TypeScript liviano que permite generar y ejecutar la aplicación Flutter directamente desde la web, sin descargas manuales. Reside en `mobile-flutter/tools/local-agent/`.

### Arquitectura

- **Servidor Express**: escucha exclusivamente en `127.0.0.1:9876` (loopback). Nunca se vincula a `0.0.0.0`.
- **Comunicación SSE**: los endpoints de generación y ejecución devuelven Server-Sent Events para progreso en tiempo real.
- **Flujo de datos**: Web → Backend (obtiene spec firmada + Flutter ZIP) → Web → Agente local (valida, extrae, ejecuta).

### Endpoints

| Método | Ruta | Función |
|---|---|---|
| `GET` | `/api/status` | Health check + estado de Flutter SDK, ADB, dispositivos |
| `GET` | `/api/devices` | Lista dispositivos conectados vía `adb devices -l` |
| `POST` | `/api/generate` | Recibe spec+ZIP, valida firma/nonce, extrae y ejecuta `flutter pub get` (SSE) |
| `POST` | `/api/run` | Ejecuta `flutter run` o `flutter build apk --release` (SSE) |

### Backend: Endpoint auxiliar

`POST /api/v1/diagrams/{id}/generation-jobs/{jobId}/agent-spec` devuelve un JSON con:
- `spec`: la `modeler-mobile-spec.json` firmada con HMAC-SHA256, incluyendo un `nonce` UUID de un solo uso.

El código Flutter se materializa en la computadora desde esa especificación. La clave de firma no se transmite al navegador: `AGENT_SIGNING_KEY` se provisiona en el entorno del agente y coincide con `MOBILE_SPEC_SECRET` del backend.

### Seguridad

- **Validación de origen**: solo acepta solicitudes desde `http://localhost:5173` o `http://127.0.0.1:5173`.
- **Firma HMAC-SHA256**: verifica que la especificación no haya sido manipulada, usando la misma lógica que `MobileSpecService`.
- **Nonce de un solo uso**: cada spec incluye un UUID `nonce` cubierto por la firma; el agente mantiene un Set de nonces usados en memoria.
- **Comandos cerrados**: el agente solo ejecuta: `flutter pub get`, `flutter analyze`, `flutter run`, `flutter build apk --release`, `adb devices`, `adb reverse tcp:8080 tcp:8080`. Nunca ejecuta comandos que vengan del servidor web.
- **Sanitización de rutas**: rechaza `..`, rutas absolutas fuera del directorio del usuario y directorios de sistema.

### Conexión al backend

**USB**: `adb reverse tcp:8080 tcp:8080` (automático). La app usa `--dart-define=API_BASE_URL=http://localhost:8080`.

**Wi-Fi**: el usuario ingresa la IP privada de la PC en el modal del agente. La app usa `--dart-define=API_BASE_URL=http://IP:8080`.

## Flutter Móvil Offline-First y Sincronización Bidireccional (Incremento 18)

La aplicación Flutter móvil y multiplataforma generada por Collab Modeler y materializada en `mobile-flutter` implementa una arquitectura **offline-first** con almacenamiento local seguro, cola transaccional (outbox) y sincronización bidireccional continua con detección y resolución visual de conflictos de concurrencia.

### 1. Almacenamiento Local SQLite (`AppDatabase`)
Gestiona una base de datos SQLite relacional (`collab_modeler_offline.db`) con soporte tanto en dispositivos móviles (Android/iOS) como en entornos headless o escritorio (Windows/Linux/macOS) mediante FFI (`sqflite_common_ffi`):
- **`cached_entities`**: Almacena instantáneas de los registros del modelo consultados o creados localmente (`entity_type`, `id`, `data`, `version`, `is_deleted`, `sync_status`, `updated_at`). Los estados de sincronización incluyen `synced`, `pending_create`, `pending_update`, `pending_delete` y `conflict`.
- **`outbox_operations`**: Registro inmutable de operaciones CRUD generadas en el dispositivo (`id` UUID v4, `entity`, `record_id`, `action`, `base_version`, `created_at`, `payload`, `status`, `retry_count`, `error_message`).
- **`conflict_records`**: Registros de conflictos de versión y concurrencia no resueltos (`id`, `operation_id`, `entity`, `record_id`, `base_version`, `server_version`, `local_payload`, `server_payload`, `conflicting_fields`, `created_at`, `status`).

### 2. Transactional Outbox y Mutaciones Optimistas (`OutboxService`)
Todas las operaciones locales de escritura (`create`, `update`, `delete`) en los repositorios de entidades:
- Se ejecutan dentro de una transacción atómica SQLite que actualiza el caché de entidades local con el estado optimista y encola la operación en `outbox_operations` con un identificador UUID idempotente y la versión base.
- Si hay conectividad, disparan en segundo plano la sincronización ascendente (`syncUp`) sin bloquear la interfaz de usuario.
- Mapean de forma transparente identificadores temporales a los IDs asignados por el servidor si la API remota genera nuevos identificadores.

### 3. Sincronización Bidireccional (`SyncService`)
- **Detección de conectividad**: Verifica disponibilidad de red mediante socket TCP y sondeo HTTP contra el backend (`http://localhost:8080` o IP configurada).
- **Sincronización ascendente (`syncUp`)**: Reenvía cronológicamente las operaciones de la outbox enviando el encabezado `Idempotency-Key: <opId>`.
- **Sincronización descendente (`syncDown`)**: Consulta periódicamente o bajo demanda los registros remotos de cada entidad registrada e incorpora actualizaciones en el caché local para aquellos registros que no tengan mutaciones locales pendientes.

### 4. Detección y Resolución de Conflictos 3-Way (`ConflictResolutionScreen`)
- **Detección**: Al procesar una actualización en `syncUp`, si el servidor reporta una versión mayor que `base_version`, se comparan campo a campo el payload local y el payload remoto:
  - Si los campos modificados localmente no chocan con cambios del servidor, convergen automáticamente.
  - Si existen campos modificados concurrentemente con valores incompatibles, la operación se marca como `CONFLICT` y se genera un registro en `conflict_records` con los campos discordantes exactos.
- **Resolución sin descarte silencioso**:
  - **Conservar local**: Reenvía los datos del dispositivo sobrescribiendo el servidor y marca el conflicto como `RESOLVED_LOCAL`.
  - **Descartar local**: Aplica la versión del servidor en el caché local, retira la operación de la outbox y marca el conflicto como `RESOLVED_SERVER`.
  - **Editar y fusionar manualmente**: Abre un diálogo de edición interactivo mostrando lado a lado los valores del servidor y los valores locales para componer un nuevo payload fusionado y sincronizarlo.

### 5. Almacenamiento Seguro de Credenciales y Sesión (`SecureStorageService`)
- Utiliza `flutter_secure_storage` (cifrado con Android Keystore / iOS Keychain / DPAPI) con mecanismo de fallback transparente en memoria/preferencias para ejecuciones de pruebas headless.
- Persiste tokens JWT de acceso (`auth_access_token`) y de refresco (`auth_refresh_token`).
- `ApiClient` intercepta respuestas HTTP 401 y renueva automáticamente el token de acceso invocando `/api/auth/refresh` sin forzar al usuario a iniciar sesión nuevamente.

## IA local móvil: texto, voz y fotografía (Incremento 19)

- `AiCommandInterpreter` transforma lenguaje natural en `AiCrudProposal`; `AiEntityRegistry` se genera desde el diagrama y centraliza nombres, tipos, obligatoriedad y enumeraciones.
- `SpeechRecognitionService` usa el reconocedor del sistema y entrega el resultado final al mismo intérprete. La disponibilidad offline depende del paquete de idioma instalado en Android.
- `LocalOcrService` valida tamaño y firma mágica, ejecuta ML Kit Text Recognition latino en el dispositivo y convierte texto/códigos/valores en una propuesta de formulario o búsqueda.
- La vista previa es la única entrada a `AiEntityRegistry.execute`. El ejecutor vuelve a validar al confirmar para impedir que una propuesta manipulada escriba datos inválidos.
- Crear, editar y eliminar usan `OutboxService`: caché optimista y operación idempotente se guardan atómicamente en SQLite. La cancelación no invoca el ejecutor.
- `/api/v1/ai/mobile-analyze` acepta opcionalmente un comando o una imagen autenticados cuando existe proveedor y red. El cliente revalida la respuesta; ante fallo vuelve inmediatamente al flujo local.
- Los logs contienen solo estados sanitizados. Imágenes, audio, tokens y texto reconocido no se registran ni se persisten como telemetría.
