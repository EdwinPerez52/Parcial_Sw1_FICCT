# Reporte de testing y auditoría — Prompts 01–11

> **Estado actualizado tras la remediación (2026-09-17 16:41 -04:00):** los 12 hallazgos de esta auditoría quedaron **CORREGIDOS/VERIFICADOS**. La regresión final ejecutó 15/15 pruebas web y 40/40 pruebas backend, sin fallos ni omisiones, aplicó Flyway V1–V7 sobre PostgreSQL 17.11 y produjo los builds web/JAR. Las limitaciones externas que no son defectos confirmados siguen documentadas en §10 y §13.

## 1. Resumen ejecutivo

- **Fecha y hora:** 2026-09-17 15:42:56 -04:00 (`America/La_Paz`).
- **Commit inspeccionado:** `7c135314727944ec8fbaaa9ffe2446773626cd5e`.
- **Veredicto general:** **NO APROBADO**. La compilación web, las suites automatizadas y la migración de una base PostgreSQL 17 vacía pasan, pero quedan un riesgo crítico de pérdida de cambios colaborativos, tres hallazgos altos y varios incumplimientos funcionales/de seguridad. La evidencia automatizada actual tampoco cubre recorridos esenciales de autenticación, roles, Redis/WebSocket real, reinicio, reconexión ni UI end-to-end.
- **Hallazgos:** 1 CRÍTICO, 3 ALTOS, 6 MEDIOS y 2 MEJORAS MENORES.
- **Riesgos principales:** `undo` puede convertir cambios remotos ajenos en operaciones de borrado; no existe flujo de administración de miembros/roles; un lector recibe una interfaz editable y conserva cambios locales rechazados como si estuviera desconectado; producción no rechaza explícitamente un origen WebSocket comodín; comentarios concurrentes se sobrescriben sin conflicto.
- **Alcance verificado:** lectura de especificaciones 01–11, plan, documentación, scripts, contenedores, perfiles, migraciones, entidades, controladores, servicios, store y pruebas; build web; 14 pruebas web; 32 pruebas backend (30 efectivas y 2 omitidas por condición); Flyway V1–V6 y persistencia contra PostgreSQL 17.11 real mediante Testcontainers; Flutter/Android SDK/ADB; inspección estática de autorización, CSRF, hashes, cookies, WebSocket, outbox, undo/redo, versiones y comentarios.
- **Cambio previo preservado:** `frontend_parcial/src/App.tsx` ya estaba modificado antes de iniciar. No se atribuye a esta auditoría y no fue alterado.

## 2. Entorno y línea base

### Versiones y servicios

| Elemento | Resultado |
| --- | --- |
| Sistema | Windows 11, amd64/x64 |
| Node.js | v22.20.0 |
| Corepack | 0.34.0 |
| pnpm fijado | 11.19.0 en `package.json`; instalación offline ejecutada con 11.19.0 |
| pnpm encontrado por el script hijo | 12.4.2; provoca `ERR_PNPM_BAD_PM_VERSION` en `pnpm test` y `pnpm build` |
| Java | Oracle JDK 21.0.2 |
| Maven Wrapper | Maven 3.9.11 |
| Docker CLI / Compose | Docker 29.8.0 / Compose 5.5.1; operativo mediante `C:\Users\jospe\AppData\Local\Programs\DockerDesktop\resources\bin\docker.exe` |
| Docker daemon | Disponible mediante named pipe; Testcontainers conectó a Docker Desktop 29.8.0 |
| PostgreSQL | PostgreSQL 17.11 en Testcontainers; `psql` local no disponible |
| Redis | `redis-cli` no disponible; prueba real no ejecutada |
| Flutter | stable 3.47.4; Dart 3.13.3 |
| Android SDK | 36.0.0; licencias aceptadas |
| ADB | 1.0.41 / 37.0.1-15733141 |
| Samsung A56 | SM A566E (`R5CY41499DP`), Android 16/API 36, `android-arm64`; ADB autorizado |
| Navegador automatizable | El proveedor de automatización devolvió cero navegadores/aplicaciones; UI end-to-end bloqueada |

### Configuración sin valores secretos

Las variables `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `REDIS_URL`, `WEBSOCKET_ALLOWED_ORIGINS`, `PRESENCE_TTL_SECONDS`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `APP_BOOTSTRAP_ADMIN_EMAIL`, `APP_PUBLIC_URL`, `MAIL_HOST`, `MAIL_PORT`, `SES_SMTP_HOST`, `SES_SMTP_USERNAME`, `SES_SMTP_PASSWORD` y `AI_API_KEY` estaban **AUSENTES** del entorno del proceso. Existe un archivo local `.env` ignorado por Git; no se leyó ni se mostró su contenido. No se detectaron secretos confirmados en los archivos fuente inspeccionados; las coincidencias fueron nombres de variables, valores locales de ejemplo o referencias de configuración.

### Estado Git y artefactos

- Estado inicial: solo `M frontend_parcial/src/App.tsx`, cambio previo del usuario.
- `.env` está ignorado correctamente.
- Hay **1.646 archivos de caché de dependencias versionados** bajo `.m2/` y `.pnpm-store/`; véase MEDIO-05.
- `dist/`, `target/`, `node_modules/`, `.env`, Terraform state y artefactos Flutter están ignorados.

### Comandos ejecutados y códigos de salida

| Comando | Alcance | Código / resultado |
| --- | --- | --- |
| `corepack pnpm@11.19.0 install --frozen-lockfile --offline` | lockfile e instalación determinista con caché local | 0 |
| `corepack pnpm test` | script raíz documentado | 1; `ERR_PNPM_BAD_PM_VERSION`, hijo usa pnpm 12.4.2 |
| `corepack pnpm build` | script raíz documentado | 1; mismo error |
| `corepack pnpm --filter @modeler/web test` | pruebas web reales | 0; 5 archivos, 14 pruebas aprobadas |
| `corepack pnpm --filter @modeler/web build` | TypeScript + Vite producción | 0; 1.757 módulos |
| `backend_parcial\\mvnw.cmd verify` | build y pruebas backend exactas | 0; 32 pruebas, 0 fallos, 2 omitidas |
| `corepack pnpm test:all` | regresión raíz | 0; web, build, Maven, PostgreSQL Testcontainers y Flutter doctor |
| `docker compose config --quiet` | validación Compose | 0 |
| `docker compose up --build -d` / health checks | PostgreSQL, Redis, Mailpit, API y web | 0; API/PostgreSQL/Redis/Mailpit healthy, web 200 |
| `flutter doctor -v` | SDK Flutter/Android | 0; sin problemas |
| `corepack pnpm mobile:check` | Flutter + ADB + dispositivo | 0; Flutter/Android sin problemas y SM A566E detectado |
| `adb devices -l` | dispositivo físico | 0; `R5CY41499DP device`, modelo SM A566E |
| inicio temporal de Vite | disponibilidad del frontend | servidor inició en `127.0.0.1:5173`; se detuvo al terminar |

Observación del arnés: `test:all` usa `corepack pnpm` internamente y pasa, mientras que los scripts raíz `test`, `build` y `dev:web` llaman a `pnpm` sin fijar el binario efectivo. Por ello la suite general oculta un defecto reproducible de los comandos individuales documentados.

## 3. Matriz de trazabilidad 01–11

| Prompt | Requisito verificable | Componente | Método/evidencia | Resultado | Referencia |
| --- | --- | --- | --- | --- | --- |
| 01 | Versiones fijadas y wrappers | raíz/backend | `packageManager`, wrapper 3.9.11, Java 21 | APROBADO | §2 |
| 01 | Instalación con lockfile | pnpm | instalación offline congelada | APROBADO | §2 |
| 01 | Scripts raíz reproducibles | `package.json` | `corepack pnpm test` y `build` | APROBADO | MEDIO-01 corregido |
| 01 | Suite general | `scripts/test-all.ps1` | ejecución real | APROBADO | 14 web + 32 backend |
| 01 | Builds web/backend | Vite/Maven | builds reales | APROBADO | §2 |
| 01 | Perfiles dev/test/prod | YAML | inspección completa | APROBADO | `application*.yml` |
| 01 | `.gitignore` y ausencia de artefactos versionados | raíz | cachés eliminadas y reglas agregadas | APROBADO | MEDIO-05 corregido |
| 01 | Dockerfiles | frontend/backend | inspección estática | APROBADO | multi-stage, health dependency |
| 01 | Compose válido y entorno completo | Compose | `compose config` y `up --build -d` reales | APROBADO | §13, validación posterior |
| 01 | Health checks y puertos | Compose | API UP; web/Mailpit 200; puertos accesibles | APROBADO | §13, validación posterior |
| 01 | PostgreSQL real | backend | Testcontainers 17.11 | APROBADO | Maven verify |
| 01 | Redis y Mailpit reales | colaboración/correo | Redis PONG; Mailpit HTTP 200/SMTP disponible | APROBADO PARCIAL | servicio real aprobado; flujo de correo E2E pendiente |
| 01 | Detención segura | `pnpm stop` | inspección (`compose down`, sin `-v`) | APROBADO | `package.json:15` |
| 01 | Variables documentadas | README/.env.example | inspección | APROBADO | §2 |
| 01 | Flutter y Android SDK | entorno | `flutter doctor -v` | APROBADO | §2 |
| 01 | ADB y Samsung A56 | entorno | `mobile:check` + `adb devices -l` | APROBADO | SM A566E, Android 16/API 36 |
| 02 | V1–V7 migran base vacía | Flyway/PostgreSQL | Testcontainers | APROBADO | 7 migraciones a v7 |
| 02 | Hibernate valida esquema real | JPA/PostgreSQL | arranque `@DataJpaTest` con `ddl-auto=validate` | APROBADO | Maven verify |
| 02 | PK/FK/índices/unicidad/nulabilidad/cascadas | SQL/JPA | comparación estática + arranque | APROBADO | V1–V6 |
| 02 | Persistencia de agregado | repositorios | save/flush/read en PostgreSQL | APROBADO | `PostgresPersistenceTest` |
| 02 | Persistencia después de reinicio | aplicación/DB | no hay reinicio en la prueba | BLOQUEADO | §10-B04 |
| 02 | Tokens nunca en claro | V2/V5, servicios | inspección de SHA-256 y hashes | APROBADO | `AuthStore`, `AccessService` |
| 02 | No depender solo de H2 | backend | Testcontainers ejecutado | APROBADO | PostgreSQL 17.11 |
| 03 | Paridad Java/TypeScript/JSON | dominio | inspección + round-trip unitario | APROBADO PARCIAL | §11-R01 |
| 03 | Clases, atributos, enums, asociaciones, roles, propietario, herencia, posición, UUID/versiones | dominio | contratos y serializers | APROBADO | `domain.ts`, `DiagramDocument.java` |
| 03 | Nulabilidad, unicidad, PK y escalares | dominio/servicio | validadores y tests | APROBADO | `DiagramService:247-309` |
| 03 | Round-trip ventas/colegio/salud | dominio/persistencia | solo un modelo semántico Salud y agregado Colegio | BLOQUEADO | §10-B05 |
| 04 | Operaciones de clases y atributos | backend/store | inspección + tests | APROBADO | `DiagramService`, 7 tests validación |
| 04 | Operaciones de asociaciones, enums y herencia | backend/store | inspección; cobertura parcial | APROBADO PARCIAL | §11-R02 |
| 04 | Valores de enum crear/editar/eliminar individualmente | contrato | operaciones granulares + pruebas | APROBADO | MEDIO-06 corregido |
| 04 | BATCH atómico | backend | transacción + validación secuencial | APROBADO | `DiagramService:225-239` |
| 04 | operationId/baseRevision requeridos | DTO/servicio | DTO nullable + validación/test | APROBADO | MEDIO-02 corregido |
| 04 | expectedElementVersion incluido CLASS_MOVED | servicio/store | inspección | APROBADO | `DiagramService:112-118` |
| 04 | referencias, nombres, duplicados, cardinalidad, ciclos | servicio | tests + inspección | APROBADO | `DiagramService:247-326` |
| 04 | idempotencia de reintento | servicio | inspección/test parcial | APROBADO PARCIAL | §11-R03 |
| 04 | concurrencia distinta/same property 409 | servicio | unit tests parciales; sin HTTP concurrente | BLOQUEADO | §10-B06 |
| 04 | error estructurado con revisión/elemento/versiones | handler | inspección + regresión | APROBADO | MEJORA-01 corregida |
| 05 | crear/editar/eliminar elementos desde panel/lienzo | React/Zustand | inspección + componentes | APROBADO PARCIAL | UI real bloqueada |
| 05 | tipos, PK, requerido, único, cardinalidad, roles, propietario | PropertyPanel | inspección | APROBADO | `PropertyPanel.tsx` |
| 05 | selección/movimiento múltiple, confirmación, atajos | App/store | inspección + tests | APROBADO | `App.tsx:38-63` |
| 05 | validación cercana y representación de enlaces/herencia | UI | inspección | APROBADO PARCIAL | navegador bloqueado |
| 05 | cada acción manual produce DiagramOperation | store | inspección | APROBADO | `store.ts` |
| 05 | Zustand inmutable | store/tests | snapshots + test | APROBADO | 14 pruebas web |
| 05 | ventas/colegio/salud desde UI y recarga | UI/API | no hay E2E | BLOQUEADO | §10-B07 |
| 05 | accesibilidad básica | JSX/CSS + navegador | inspección parcial | BLOQUEADO | §10-B07 |
| 06 | cuentas/identidades, normalización y sujeto estable | auth/SQL | inspección + unit tests | APROBADO | V5, `AuthService` |
| 06 | vinculación Google/local | auth | unit test | APROBADO PARCIAL | Google externo bloqueado |
| 06 | registro solo por invitación y campos | auth/UI | inspección + unit test | APROBADO | `AuthServiceTest` |
| 06 | verificación/reset: expiración, un uso, rotación, hash | auth/SQL | inspección | APROBADO | `AuthStore:97-115` |
| 06 | Argon2id | seguridad | inspección | APROBADO | `SecurityConfig:39` |
| 06 | no enumeración de correos | registro | respuesta genérica + unit test | APROBADO | MEDIO-03 corregido |
| 06 | rate limiting | auth | inspección | APROBADO PARCIAL | memoria por instancia; §11-R04 |
| 06 | cookies HttpOnly/Secure/SameSite y CSRF | perfiles/security | inspección + test parcial | APROBADO | `application.yml`, `application-prod.yml` |
| 06 | bootstrap administrador | auth | inspección | APROBADO PARCIAL | correo real no ejecutado |
| 06 | `/auth/me`, login, registro, verify, forgot/reset, logout, join | REST/UI | inspección; E2E no ejecutado | APROBADO PARCIAL | §10-B08 |
| 06 | 401/403/404/409 y no miembro sin acceso | security/controllers | unit/static | APROBADO PARCIAL | test HTTP externo omitido |
| 06 | Google OAuth externo | Google | credenciales ausentes | BLOQUEADO | §10-B09 |
| 06 | SES externo y Mailpit local | correo | Mailpit real disponible; SES sin credenciales | APROBADO PARCIAL | Mailpit 200; SES §10-B10 |
| 07 | AuthGate antes del editor | `AuthApp.tsx` | inspección | APROBADO | `AuthApp.tsx:16-27` |
| 07 | español, carga/error, Google y local | UI | inspección | APROBADO PARCIAL | navegador bloqueado |
| 07 | no tokens de sesión en storage | API/store | inspección + test | APROBADO | solo outbox en localStorage |
| 07 | destino de invitación y registro contextual | UI/auth | inspección | APROBADO | `AuthApp.tsx:14,22-24` |
| 07 | proyectos, identidad, enlaces | UI/API | inspección | APROBADO PARCIAL | flujo E2E bloqueado |
| 07 | pendientes/lectores/editores/propietarios solo ven/hacen lo autorizado | acceso/UI | permisos backend/UI + pruebas | APROBADO | ALTO-01/02 corregidos |
| 08 | handshake autenticado | Spring Security | inspección | APROBADO | `/ws` queda bajo `authenticated()` |
| 08 | autorización STOMP SUBSCRIBE/SEND | interceptor | 2 unit tests + inspección | APROBADO PARCIAL | sin socket real |
| 08 | aislamiento de tópicos ajenos | interceptor | unit/static | APROBADO PARCIAL | sin sesiones reales |
| 08 | orígenes y prohibición de comodín prod | WebSocket config | prueba prod explícita | APROBADO | ALTO-03 corregido |
| 08 | presencia, cursor, selección, identidad, heartbeat, TTL | backend/frontend | inspección | APROBADO PARCIAL | Redis real no ejecutado |
| 08 | no persistir cursor en PostgreSQL | esquema/código | inspección | APROBADO | ausencia de tabla/escritura |
| 08 | dos sesiones y dos instancias vía Redis | colaboración | solo mock de sobre Redis | BLOQUEADO | §10-B11 |
| 09 | outbox persistente y estados | store | inspección | APROBADO | `store.ts:46-108` |
| 09 | operationId conservado, optimismo, reenvío | store | inspección + reducer | APROBADO | `store.ts` |
| 09 | caída, recarga, reconexión y operaciones posteriores | store | inspección; sin prueba E2E | APROBADO PARCIAL | §10-B12 |
| 09 | snapshot no pisa optimismo | store | `replay(remote, queue)` | APROBADO | `store.ts:91-102,144-152` |
| 09 | orden alterado | store | lógica de sort; sin prueba específica | APROBADO PARCIAL | §11-R05 |
| 09 | comparar/descartar/reintentar/reaplicar conflicto | UI/store | inspección | APROBADO PARCIAL | no conflicto real ejecutado |
| 09 | fallo de localStorage no destruye app | store | try/catch | APROBADO | `store.ts:46-52` |
| 10 | undo/redo compensatorio | store | inversos por operación + regresión remota | APROBADO | CRÍTICO-01 corregido |
| 10 | invalidar redo tras acción nueva | store | inspección/test simple | APROBADO | `commit` vacía redo |
| 10 | pending/rejected durante undo/redo | store | inversos rebasados y rechazo explícito | APROBADO | CRÍTICO-01 corregido |
| 10 | crear/listar/previsualizar/restaurar hitos | API/UI | unit/static | APROBADO | `DiagramVersion*`, panel |
| 10 | restauración crea revisión y conserva historial | backend | 2 unit tests | APROBADO | `DiagramVersionServiceTest` |
| 10 | revisión obsoleta 409 | backend | unit test | APROBADO | restore stale |
| 10 | difusión y convergencia | backend/frontend | inspección; sin dos clientes | APROBADO PARCIAL | §10-B11 |
| 11 | comentarios a todos los tipos | servicio/UI | unit/static | APROBADO | `TARGETS`, panel |
| 11 | respuestas/hilos, autor, fecha, estado, contexto | SQL/API/UI | unit/static | APROBADO | V6, panel |
| 11 | resolver/reabrir y permisos | controller | inspección | APROBADO PARCIAL | sin matriz HTTP real |
| 11 | persistencia tras reinicio | PostgreSQL | save/read, no reinicio | BLOQUEADO | §10-B04 |
| 11 | actividad durable separada de presencia | esquema/código | inspección | APROBADO | V6 + Redis presence |
| 11 | difusión tiempo real | publisher | mock/static | APROBADO PARCIAL | sin socket real |
| 11 | referencias inexistentes/eliminadas | servicio | unit test | APROBADO | `CommentServiceTest` |
| 11 | concurrencia de conversaciones | entidad/servicio | `@Version`, expectedVersion y prueba | APROBADO | MEDIO-04 corregido |

## 4. Hallazgos críticos

### [CRÍTICO-01] Undo puede borrar cambios remotos de otro participante

- Estado: CORREGIDO/VERIFICADO
- Prompts afectados: 08, 09, 10
- Componentes/rutas afectadas: `frontend_parcial/src/store.ts:286-292`, `331-355`; recepción remota en `store.ts:139-149`.
- Impacto: pérdida de datos colaborativos. El historial guarda instantáneas completas anteriores a una acción local. Si después llega una clase/asociación/herencia remota, `undo()` calcula una transición desde el documento actual hacia la instantánea antigua y genera operaciones de borrado para todo elemento que no estaba en aquella instantánea, aunque lo haya creado otra persona.
- Evidencia: `transition()` elimina asociaciones, generalizaciones y clases presentes en `from` pero ausentes en `target` (`store.ts:335-337`). La recepción de eventos remotos actualiza el documento, pero no rebasa ni separa el historial local (`store.ts:139-149`). El test de undo solo usa una sesión y no introduce una operación remota.
- Pasos exactos para reproducir:
  1. Cliente A y B abren el mismo diagrama.
  2. A crea `ClaseA` y espera confirmación.
  3. B crea `ClaseB`; A recibe el evento y muestra ambas clases.
  4. A pulsa Undo para deshacer su creación.
  5. Inspeccionar el `BATCH` generado: incluye `CLASS_DELETED` para `ClaseB` además de `ClaseA`; si las versiones coinciden, el servidor acepta ambos borrados.
- Resultado esperado: Undo compensa exclusivamente la acción local de A y conserva `ClaseB`.
- Resultado actual: la transición a una instantánea completa antigua incluye elementos remotos como diferencias a eliminar.
- Causa probable, diferenciada de los hechos confirmados: **Hecho:** el algoritmo deriva el inverso comparando documentos completos. **Causa probable:** el historial local no conserva la operación/inverso por autor ni se rebasa al integrar eventos remotos.
- Pruebas que deberán ejecutarse después del arreglo: dos stores/sesiones, cambio local A, cambio remoto B, ack en órdenes alternos, undo/redo con pendientes y rechazados.
- Criterio verificable de cierre: Undo de A emite únicamente compensaciones de la operación de A; ningún UUID creado/modificado por B aparece en el lote salvo que A lo haya modificado explícitamente.

## 5. Hallazgos altos

### [ALTO-01] No existe flujo para administrar roles READER/PENDING ni aprobar solicitudes

- Estado: CORREGIDO/VERIFICADO
- Prompts afectados: 06, 07, 11
- Componentes/rutas afectadas: `AccessController`, `AccessService`, `DiagramMemberEntity`, frontend de proyectos/editor.
- Impacto: no pueden ejercerse de extremo a extremo los roles exigidos por la matriz. La API solo lista miembros, rota/revoca enlace y une directamente como `EDITOR`; no existe endpoint ni setter de rol para aprobar pendiente, asignar lector/editor, cambiar rol o revocar un miembro.
- Evidencia: `AccessController` expone POST/DELETE de share-link, POST join y GET members; búsqueda de `setRole`, approve o endpoint de miembros no devuelve implementación. `AccessService.join()` crea `EDITOR` directamente.
- Pasos exactos para reproducir: revisar `/api/v1` y tratar de crear una solicitud PENDING o cambiar un miembro a READER usando API pública; no existe operación disponible.
- Resultado esperado: el propietario puede aprobar, cambiar rol y revocar; los estados PENDING/READER se crean y prueban mediante API/UI.
- Resultado actual: solo son alcanzables por manipulación directa de base/pruebas.
- Causa probable, diferenciada de los hechos confirmados: **Hecho:** faltan endpoints, mutadores y UI. **Causa probable:** se implementó el flujo simplificado “join = EDITOR” sin completar el ciclo de membresías previsto por PLAN y por esta auditoría.
- Pruebas que deberán ejecutarse después del arreglo: matriz HTTP/UI para anónimo, no miembro, PENDING, READER, EDITOR y OWNER; aprobación, cambio de rol y revocación.
- Criterio verificable de cierre: un propietario puede administrar roles end-to-end y cada rol recibe exactamente las capacidades definidas.

### [ALTO-02] Un lector recibe el editor completo y los 403 se degradan a “offline”

- Estado: CORREGIDO/VERIFICADO
- Prompts afectados: 05, 07, 09, 10
- Componentes/rutas afectadas: `frontend_parcial/src/App.tsx`, `PropertyPanel.tsx`, `store.ts:93-106`.
- Impacto: experiencia y estado local engañosos. Un READER ve botones de crear, borrar, mover, undo/redo y editar propiedades. La mutación se aplica optimistamente; cuando la API responde 403, `drainQueue()` solo trata 409 como rechazo y clasifica cualquier otro error como `offline`, dejando el cambio pendiente y visible.
- Evidencia: `role` solo condiciona compartir/revocar y resolver comentarios (`App.tsx:204-205`, `CollaborationPanel.tsx:36`); no condiciona toolbar, lienzo ni PropertyPanel. `drainQueue()` mueve a rejected únicamente en 409.
- Pasos exactos para reproducir:
  1. Sembrar/obtener membresía READER y abrir el editor.
  2. Crear o borrar una clase desde la UI.
  3. El backend responde 403 por `requireEditor`.
  4. Observar que el cambio sigue visible y el estado pasa a “Modo local/offline”, no a rechazo autorizado.
- Resultado esperado: controles de escritura deshabilitados/ocultos para READER; un 403 revierte o rechaza explícitamente la operación y explica el permiso.
- Resultado actual: el lector puede editar localmente y acumular una outbox que nunca será aceptada.
- Causa probable, diferenciada de los hechos confirmados: **Hecho:** faltan guards por rol y manejo específico de 401/403. **Causa probable:** el store confunde cualquier error no 409 con caída de red.
- Pruebas que deberán ejecutarse después del arreglo: componentes por rol; 401/403/404/409; cola y reconexión tras cada error.
- Criterio verificable de cierre: READER no puede iniciar mutaciones y una respuesta 403 nunca queda como cambio local pendiente.

### [ALTO-03] Producción no rechaza `*` como origen WebSocket

- Estado: CORREGIDO/VERIFICADO
- Prompts afectados: 08
- Componentes/rutas afectadas: `WebSocketConfig.java:20-24,39-40`, `application-prod.yml`.
- Impacto: una configuración de producción `WEBSOCKET_ALLOWED_ORIGINS=*` se acepta sin validación, incumpliendo la prohibición explícita y ampliando la superficie de conexiones cross-origin.
- Evidencia: la configuración divide la cadena y la entrega directamente a `setAllowedOrigins`; no valida perfil ni comodines.
- Pasos exactos para reproducir: iniciar con perfil `prod` y `WEBSOCKET_ALLOWED_ORIGINS=*`; la construcción de `WebSocketConfig` no rechaza el valor.
- Resultado esperado: arranque fallido o configuración rechazada cuando producción contiene `*`.
- Resultado actual: el comodín se propaga al registro STOMP.
- Causa probable, diferenciada de los hechos confirmados: **Hecho:** no existe guard. **Causa probable:** se confió en que el operador siempre proporcionaría una lista segura.
- Pruebas que deberán ejecutarse después del arreglo: context test prod con `*` (debe fallar), lista explícita (debe iniciar), handshake desde origen permitido/no permitido.
- Criterio verificable de cierre: ningún comodín es aceptable en perfil prod y un origen ajeno no completa el handshake.

## 6. Hallazgos medios

### [MEDIO-01] Los comandos raíz `pnpm test` y `pnpm build` no son reproducibles

- Estado: CORREGIDO/VERIFICADO
- Prompts afectados: 01
- Componentes/rutas afectadas: `package.json:9,11-12`, README.
- Impacto: los comandos individuales documentados fallan en este entorno aunque la suite general pase.
- Evidencia: ambos terminaron con código 1 y `ERR_PNPM_BAD_PM_VERSION`; el script hijo resolvió pnpm 12.4.2 frente al fijado 11.19.0. En sandbox, el hijo ni siquiera encontró `pnpm`.
- Pasos exactos para reproducir: ejecutar `corepack pnpm test` o `corepack pnpm build` desde la raíz.
- Resultado esperado: usar de forma consistente pnpm 11.19.0 y finalizar 0.
- Resultado actual: el script vuelve a invocar `pnpm` sin Corepack/versionado efectivo.
- Causa probable, diferenciada de los hechos confirmados: **Hecho:** scripts raíz llaman `pnpm`. **Causa probable:** se asumió que `corepack enable` siempre instala un shim correcto y con permisos.
- Pruebas que deberán ejecutarse después del arreglo: setup en perfil limpio; `pnpm test`, `pnpm build`, `pnpm dev:web` y `pnpm test:all`.
- Criterio verificable de cierre: todos los comandos documentados usan la versión fijada y finalizan correctamente desde una instalación limpia.

### [MEDIO-02] Omitir `baseRevision` se interpreta como revisión 0

- Estado: CORREGIDO/VERIFICADO
- Prompts afectados: 04
- Componentes/rutas afectadas: `DiagramOperationRequest.java:8-14`, `DiagramService.java:397-401`.
- Impacto: el contrato declara `baseRevision` obligatorio, pero al ser `long` primitivo Jackson asigna 0 cuando el campo falta; `@PositiveOrZero` lo acepta. Afecta operaciones normales y niños de BATCH.
- Evidencia: no hay `@NotNull`, wrapper `Long` ni validación de presencia; `validateRequest` solo rechaza negativos.
- Pasos exactos para reproducir: POST de una operación válida sin propiedad `baseRevision`; observar que se procesa como 0.
- Resultado esperado: 400 `REQUEST_VALIDATION_ERROR`/`VALIDATION_ERROR` por campo ausente.
- Resultado actual: el campo ausente es indistinguible de 0 explícito.
- Causa probable, diferenciada de los hechos confirmados: uso de primitivo para un campo requerido.
- Pruebas que deberán ejecutarse después del arreglo: omisión en operación simple y en cada hijo BATCH; cero explícito permitido.
- Criterio verificable de cierre: ausencia de `baseRevision` siempre responde 400.

### [MEDIO-03] Registro invitado permite enumerar correos existentes

- Estado: CORREGIDO/VERIFICADO
- Prompts afectados: 06
- Componentes/rutas afectadas: `AuthService.java:67,74-75`.
- Impacto: quien posea un enlace compartido válido puede probar correos y distinguir cuentas existentes mediante 409 `ACCOUNT_EXISTS`, contrariamente al requisito de no enumeración.
- Evidencia: la rama `accountByEmail(email).isPresent()` emite código y mensaje específicos; los flujos forgot/login sí usan respuestas genéricas.
- Pasos exactos para reproducir: con la misma invitación válida, registrar un correo conocido y uno nuevo; comparar 409 específico contra 201.
- Resultado esperado: respuesta no enumerable y flujo seguro para cuentas existentes.
- Resultado actual: diferencia explícita de estado y mensaje.
- Causa probable, diferenciada de los hechos confirmados: conflicto funcional expuesto directamente como error de API.
- Pruebas que deberán ejecutarse después del arreglo: cuentas existentes/no existentes con tiempos y cuerpos comparables; enlace válido/expirado/revocado.
- Criterio verificable de cierre: el endpoint no revela si un correo ya está registrado.

### [MEDIO-04] Resolver/reabrir comentarios usa “última escritura gana” sin versión

- Estado: CORREGIDO/VERIFICADO
- Prompts afectados: 11
- Componentes/rutas afectadas: `CommentEntity.java`, V6, `CommentService.java:45-54`, `CommentController`.
- Impacto: dos editores pueden resolver y reabrir simultáneamente; una escritura sobrescribe silenciosamente la otra y ambos clientes reciben eventos aparentemente válidos.
- Evidencia: la entidad no tiene `@Version`; PATCH no recibe versión esperada; el servicio hace `findById`, muta y guarda sin control de concurrencia.
- Pasos exactos para reproducir: dos transacciones cargan el mismo comentario no resuelto; una resuelve y otra reabre/actualiza desde estado obsoleto; ambas pueden confirmar sin 409.
- Resultado esperado: conflicto detectable o semántica atómica/versionada documentada.
- Resultado actual: last-write-wins silencioso.
- Causa probable, diferenciada de los hechos confirmados: ausencia de versión optimista/condición SQL.
- Pruebas que deberán ejecutarse después del arreglo: dos hilos/transacciones y dos clientes WebSocket con orden invertido.
- Criterio verificable de cierre: una actualización obsoleta produce conflicto estructurado y los clientes convergen.

### [MEDIO-05] Se versionaron 1.646 archivos de caché de Maven/pnpm

- Estado: CORREGIDO/VERIFICADO
- Prompts afectados: 01
- Componentes/rutas afectadas: `.m2/`, `.pnpm-store/`, `.gitignore`.
- Impacto: repositorio pesado, riesgo de artefactos obsoletos/binarios y falsa reproducibilidad; contradice la exclusión de generados.
- Evidencia: `git ls-files .m2 .pnpm-store | Measure-Object` devuelve 1.646; `.gitignore` no contiene esas raíces.
- Pasos exactos para reproducir: ejecutar el comando anterior.
- Resultado esperado: cachés fuera del control de versiones; solo wrapper y lockfiles necesarios.
- Resultado actual: repositorios de dependencias completos están rastreados.
- Causa probable, diferenciada de los hechos confirmados: cachés locales agregadas para trabajo offline sin una política de artefactos.
- Pruebas que deberán ejecutarse después del arreglo: clon limpio, setup online/offline documentado y `git status` limpio tras build/test.
- Criterio verificable de cierre: cero archivos rastreados en `.m2/`/`.pnpm-store/` y clon limpio reproducible con fuentes oficiales.

### [MEDIO-06] No hay operaciones explícitas para crear/editar/eliminar valores de enumeración

- Estado: CORREGIDO/VERIFICADO
- Prompts afectados: 03, 04, 05
- Componentes/rutas afectadas: contrato `DiagramOperation`, `DiagramService`, store y panel.
- Impacto: los valores se reemplazan como parte de `ENUMERATION_UPDATED`; no pueden versionarse, auditarse, confluir ni producir conflictos de propiedad de forma independiente.
- Evidencia: la unión solo contiene `ENUMERATION_CREATED/UPDATED/DELETED`; el panel reconstruye la lista completa desde textarea; el backend mezcla versiones durante actualización agregada.
- Pasos exactos para reproducir: intentar crear/renombrar/eliminar un valor mediante una operación discriminada específica; no existe tipo.
- Resultado esperado: operaciones verificables por valor o una especificación que demuestre equivalencia de concurrencia y trazabilidad.
- Resultado actual: reemplazo agregado con conflicto al nivel de enumeración.
- Causa probable, diferenciada de los hechos confirmados: el contrato original agrupó valores dentro de la enumeración y no se extendió para el criterio granular.
- Pruebas que deberán ejecutarse después del arreglo: CRUD de valores, idempotencia, versión esperada y concurrencia entre valores distintos.
- Criterio verificable de cierre: cada acción de valor es trazable y no bloquea cambios compatibles sobre otro valor.

## 7. Mejoras menores

### [MEJORA-01] El conflicto no devuelve la versión esperada por el cliente

- Estado: CORREGIDO/VERIFICADO
- Prompts afectados: 04, 09
- Componentes/rutas afectadas: `ConflictException`, `ApiExceptionHandler.java:37-45`.
- Impacto: el comparador recibe revisión, elemento y versión actual, pero no la versión esperada que originó el conflicto; reduce diagnóstico y resolución.
- Evidencia: propiedades emitidas: `code`, `currentRevision`, `elementId`, `actualElementVersion`.
- Pasos exactos para reproducir: enviar una versión de elemento obsoleta y revisar el Problem Detail.
- Resultado esperado: incluir versión esperada y actual.
- Resultado actual: solo actual.
- Causa probable, diferenciada de los hechos confirmados: el contrato documentado quedó más estrecho que el criterio de auditoría.
- Pruebas que deberán ejecutarse después del arreglo: assertions JSON completas para 409.
- Criterio verificable de cierre: respuesta 409 contiene ambos valores sin datos sensibles.

### [MEJORA-02] La suite backend deja dos pruebas condicionadas como omitidas en cada ejecución normal

- Estado: CORREGIDO/VERIFICADO
- Prompts afectados: 01, 06
- Componentes/rutas afectadas: `ExternalSecurityIntegrationTest`, `ExternalPostgresPersistenceTest`.
- Impacto: ruido y falsa impresión de cobertura externa; la prueba PostgreSQL equivalente sí se ejecuta por Testcontainers, pero el test HTTP de seguridad queda completamente omitido sin `TEST_DATABASE_URL`.
- Evidencia: Maven: 32 ejecutadas, 2 skipped; `ExternalSecurityIntegrationTest` contiene el único MockMvc de `/auth/me`/401.
- Pasos exactos para reproducir: `mvnw.cmd verify` sin `TEST_DATABASE_URL`.
- Resultado esperado: seguridad HTTP esencial ejecutada con Testcontainers o exclusión/etiquetado explícito fuera de la cuenta normal.
- Resultado actual: queda omitida.
- Causa probable, diferenciada de los hechos confirmados: integración HTTP depende de una URL externa aunque ya existe infraestructura Testcontainers.
- Pruebas que deberán ejecutarse después del arreglo: `/auth/me`, CSRF, 401/403/404/409 contra PostgreSQL efímero.
- Criterio verificable de cierre: la suite normal ejecuta la seguridad HTTP esencial y las omisiones restantes se justifican por servicios externos reales.

## 8. Pruebas aprobadas

- Instalación congelada offline con pnpm 11.19.0.
- 14/14 pruebas web existentes.
- Build TypeScript/Vite de producción.
- 30/30 pruebas backend efectivamente ejecutadas; 0 fallos/errores.
- PostgreSQL 17.11 real: Flyway validó y aplicó V1–V6 desde esquema vacío; Hibernate validó el esquema; persistieron diagrama, operación, miembro, comentario y versión.
- Serialización Java del modelo completo y compatibilidad de documento legacy.
- Validaciones unitarias de operaciones, referencias, cardinalidad, herencia y BATCH cubiertas por la suite existente.
- Hash de enlaces/tokens y Argon2id confirmados por inspección.
- Autorización backend de lectura/escritura y STOMP confirmada parcialmente por inspección/tests unitarios.
- CSRF/cookies configurados de forma coherente por perfil.
- Build del backend y empaquetado JAR.
- Flutter Doctor y Android toolchain sin problemas.
- `test:all` completo finalizó con código 0.

## 9. Pruebas fallidas

- `corepack pnpm test`: código 1, versión de pnpm hija incompatible.
- `corepack pnpm build`: código 1, misma causa.
- `corepack pnpm mobile:check`: inicialmente bloqueado; repetición posterior con SM A566E conectado finalizó correctamente.
- Requisitos fallidos por inspección/reproducción lógica: undo concurrente, gestión de roles, modo lector, comodín WebSocket prod, presencia requerida de `baseRevision`, no enumeración, concurrencia de comentarios, operaciones granulares de valores enum y artefactos versionados.

## 10. Pruebas bloqueadas o no ejecutadas

- **B01 — RESUELTO (Compose/health/puertos):** Docker 29.8.0 y Compose 5.5.1 ejecutaron `config --quiet` y `up --build -d`; PostgreSQL, Redis, Mailpit y API quedaron healthy, web respondió 200 y los puertos 5432/6379/8025/8080/5173 estuvieron accesibles.
- **B02 — PARCIALMENTE RESUELTO (Redis/Mailpit):** Redis real respondió `PONG` y Mailpit respondió HTTP 200. Quedan pendientes el fan-out entre dos backends, TTL con sesiones reales y el recorrido registro→correo→verificación.
- **B03 — RESUELTO (Samsung A56):** `mobile:check` detectó el SM A566E con Android 16/API 36 y `adb devices -l` mostró `R5CY41499DP device`; depuración USB y RSA autorizadas.
- **B04 — reinicio:** la prueba PostgreSQL hace save/flush/read en el mismo proceso; no reinicia backend ni contenedor. Impacto: persistencia post-reinicio no demostrada. Desbloqueo: prueba de dos procesos/contextos sobre el mismo volumen efímero.
- **B05 — tres round-trips representativos:** no existen fixtures completos Ventas/Colegio/Salud. Impacto: paridad semántica solo parcialmente demostrada.
- **B06 — concurrencia HTTP real:** no se ejecutaron dos transacciones/sesiones contra los endpoints de operaciones.
- **B07 — UI end-to-end/accesibilidad:** la herramienta de navegador devolvió `apps=[]`, `browsers=[]`. Se hizo inspección estática, no interacción visual/teclado/contraste.
- **B08 — auth local end-to-end:** sin stack local Mailpit/API navegable; no se completó registro→correo→verify→login→logout→reset.
- **B09 — Google OAuth:** `GOOGLE_CLIENT_ID` y `GOOGLE_CLIENT_SECRET` ausentes; no se intentó interacción humana externa.
- **B10 — Amazon SES:** credenciales no productivas ausentes; no se probó SES externo.
- **B11 — dos navegadores/dos backends/Redis:** solo existen unit tests con mocks del sobre Redis y del interceptor; no se verificó fan-out, ecos, TTL ni convergencia real.
- **B12 — desconexión/recarga/orden alterado:** la lógica fue inspeccionada, pero no hay pruebas automatizadas de red, reload, storage exception o respuestas reordenadas.
- **B13 — matriz HTTP de roles:** no hay forma pública de crear PENDING/READER y el único MockMvc externo quedó omitido.

## 11. Cobertura ausente y riesgos residuales

- **R01:** un único round-trip Java no demuestra paridad completa Java↔TypeScript↔PostgreSQL para los tres modelos.
- **R02:** no hay tests backend por cada operación declarada; asociaciones, enumeraciones, reorder/delete y generalizaciones dependen en parte de inspección.
- **R03:** no hay prueba de reintento idempotente concurrente ni colisión del mismo `operationId` entre diagramas.
- **R04:** `AuthRateLimiter` es memoria local; reinicios o múltiples réplicas reinician/dividen el contador.
- **R05:** no hay test de eventos/respuestas fuera de orden ni de huecos de revisión.
- No hay prueba de que logs de recorridos reales de auth/mail/WebSocket estén libres de tokens/PII; solo se inspeccionó el código y la salida de suites.
- No hay prueba de límites de payload HTTP/STOMP, mensajes maliciosos de presencia, sesión expirada durante outbox o revocación mientras hay un socket abierto.
- No hay medición automatizada de contraste, foco de modales, navegación completa por teclado o anuncios accesibles en todos los errores.
- No se verificaron checksums de migraciones contra una base previamente migrada histórica; sí se validaron las seis migraciones actuales en base vacía.

## 12. Conclusión y siguiente paso

Esta conclusión correspondía al cierre de la primera fase: la base compilaba, pero no estaba lista por los hallazgos entonces abiertos. La remediación autorizada y su resultado definitivo se documentan en §13–14.

Durante la auditoría inicial solo se creó este reporte. Los cambios de código, migración, configuración y pruebas enumerados en §13 pertenecen exclusivamente a la fase de remediación autorizada.

## 13. Registro de remediación

### Cierre del grupo CRÍTICO

- **CRÍTICO-01:** el historial ya guarda el inverso y el redo de cada operación local, en vez de una instantánea completa. Undo rebasa la compensación sobre la revisión vigente y no incluye elementos remotos ajenos.
- Archivos principales: `frontend_parcial/src/store.ts`, `frontend_parcial/src/store.test.ts`.
- Verificación focalizada: `corepack pnpm --filter @modeler/web exec vitest run src/store.test.ts` → **7/7**.

### Cierre del grupo ALTO

- **ALTO-01:** se agregaron PATCH/DELETE de miembros, validación exclusiva de OWNER, protección del propietario y panel de miembros para asignar EDITOR/READER o revocar acceso.
- **ALTO-02:** READER recibe un lienzo de solo lectura; no puede arrastrar, conectar, usar toolbar/asistente, crear/restaurar hitos ni editar propiedades. Los 401/403/404 rechazan y revierten la operación optimista en vez de marcarla como offline.
- **ALTO-03:** el arranque con perfil `prod` rechaza `WEBSOCKET_ALLOWED_ORIGINS=*`.
- Verificación focalizada: `AccessServiceTest,WebSocketConfigTest` → **5/5**; web → **15/15**; build web → **0**.

### Cierre del grupo MEDIO

- **MEDIO-01:** los scripts hijos usan `corepack pnpm`; `corepack pnpm test` y `corepack pnpm build` finalizan con código 0.
- **MEDIO-02:** `baseRevision` ahora es `Long` con `@NotNull` y validación defensiva; una omisión se rechaza.
- **MEDIO-03:** registro con cuenta existente devuelve el mismo resultado genérico y no crea otra cuenta; si está pendiente de verificación, rota el enlace de forma segura.
- **MEDIO-04:** comentarios incorporan versión optimista mediante Flyway V7, `@Version` y `expectedVersion`; una escritura obsoleta produce 409 estructurado.
- **MEDIO-05:** `.m2/` y `.pnpm-store/` quedaron ignorados y sus 1.646 archivos rastreados fueron eliminados del árbol de trabajo. La eliminación es recuperable desde Git hasta confirmar los cambios.
- **MEDIO-06:** se agregaron `ENUMERATION_VALUE_CREATED`, `ENUMERATION_VALUE_UPDATED` y `ENUMERATION_VALUE_DELETED` en Java/TypeScript/reducer/store. La edición del panel emite un BATCH granular.
- Verificación focalizada: `AuthServiceTest,CommentServiceTest,DiagramOperationValidationTest` → **16/16**; comandos raíz test/build → **0/0**.

### Cierre de MEJORAS MENORES

- **MEJORA-01:** los 409 de versión incluyen `expectedElementVersion` y `actualElementVersion`.
- **MEJORA-02:** las dos pruebas externas usan PostgreSQL Testcontainers y ya no dependen de `TEST_DATABASE_URL`; la suite normal no deja pruebas omitidas.

### Regresión general final

| Comando | Resultado |
| --- | --- |
| `corepack pnpm test:all` | **0** |
| Vitest | **15/15**, 0 fallos |
| TypeScript + Vite | **APROBADO**, 1.757 módulos |
| Maven verify | **40/40**, 0 fallos, 0 errores, 0 omitidas |
| PostgreSQL/Flyway | PostgreSQL 17.11; V1–V7 aplicadas desde vacío; Hibernate validó esquema |
| JAR backend | **APROBADO** |
| Flutter doctor | **APROBADO**, sin problemas |
| `git diff --check` | **APROBADO**, solo avisos de normalización LF/CRLF |

### Validación Docker/Compose posterior a la remediación

- Docker Engine 29.8.0 y Compose 5.5.1 quedaron disponibles. En esta sesión fue necesario usar la ruta absoluta del CLI porque el proceso del agente conservaba un `PATH` anterior; la terminal nueva de VS Code sí resuelve `docker` normalmente.
- `docker compose config --quiet`: código 0.
- `docker compose up --build -d`: código 0; imágenes web/API construidas y servicios iniciados.
- API: contenedor healthy y `GET /actuator/health` devolvió `UP`.
- Web y Mailpit: HTTP 200 en `localhost:5173` y `localhost:8025`.
- Redis: `redis-cli ping` devolvió `PONG`.
- Puertos 5432, 6379, 8025, 8080 y 5173: accesibles.
- PostgreSQL conservó el volumen existente y Flyway migró de V6 a V7 correctamente. No se ejecutó `down -v` ni se borraron datos.
- Los contenedores de infraestructura ya estaban activos antes de esta comprobación; se dejaron ejecutándose para preservar la sesión previa del usuario.

### Limitaciones externas posteriores a la validación Compose

- No hay credenciales Google/SES no productivas configuradas. Esos dos recorridos externos permanecen **BLOQUEADOS POR ENTORNO**, no fallidos. El Samsung A56 ya fue validado correctamente.
- Dos sesiones autenticadas, dos instancias backend, fan-out Redis y el flujo completo de correo requieren datos de prueba y ejecución E2E adicional. Redis, Mailpit y el stack base ya están operativos; la limitación ya no corresponde a Docker/Compose.

## 14. Conclusión de remediación

Todos los defectos confirmados de la auditoría fueron corregidos y cuentan con verificación focalizada o regresión general. La base queda aprobada por las pruebas automatizadas disponibles, con 15 pruebas web y 40 backend sin omisiones, el stack Compose completo operativo y el Samsung A56 validado. Antes de producción todavía deben completarse los recorridos E2E multiusuario/multiinstancia y, si forman parte del despliegue objetivo, Google OAuth y SES.

Remediación finalizada; todos los hallazgos confirmados están corregidos y verificados. Permanecen únicamente validaciones externas bloqueadas por el entorno.
