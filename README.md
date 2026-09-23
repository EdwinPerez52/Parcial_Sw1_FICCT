# Collab Modeler

Editor UML colaborativo con React/TypeScript, Spring Boot/Java 21, PostgreSQL y Redis.

## Estructura

`Backend-web/`, `Frontend-web/` y `mobile-flutter/` contienen respectivamente el backend, frontend y aplicación Flutter con sus archivos internos. La infraestructura declarativa vive en `infra/`, las automatizaciones compartidas en `scripts/` y la documentación en `docs/`. Consulta los [límites de módulos](./docs/module-boundaries.md) antes de crear dependencias entre componentes.

Para aprender a utilizar el producto, abre el [manual de usuario web y móvil](./documentacion%20proyecto/manual-usuario.html).

## Requisitos

- Node.js 22 o superior y Corepack.
- Java JDK 21. No hace falta instalar Maven: `Backend-web/mvnw.cmd` (Windows) y `Backend-web/mvnw` fijan Maven 3.9.11.
- Docker Desktop con Docker Compose para PostgreSQL, Redis y el entorno completo.
- Para Android: Flutter SDK estable, Android SDK Platform Tools y `adb` en `PATH`.

Comprueba las herramientas con `node --version`, `java -version`, `docker version`, `flutter doctor -v` y `adb version`.

## Instalación reproducible

```powershell
corepack enable
corepack prepare pnpm@11.19.0 --activate
pnpm setup
```

`pnpm setup` instala exactamente el lockfile web, descarga la distribución Maven fijada y obtiene las dependencias del backend. Copia `.env.example` como `.env` solo si necesitas sobrescribir valores locales. Nunca confirmes secretos en Git.

## Ejecución

Entorno completo, con frontend en <http://localhost:5173> y API en <http://localhost:8080>:

```powershell
pnpm dev
```

La salud del backend se consulta en <http://localhost:8080/actuator/health>. PostgreSQL de Docker escucha en `5433` para no interferir con instalaciones locales en `5432`; Redis escucha en `6379` y Mailpit muestra los correos locales en <http://localhost:8025>.

Para desarrollar cada proceso por separado:

```powershell
pnpm dev:infra
pnpm dev:api
pnpm dev:web
```

`dev:api` carga las variables locales de `.env` sin imprimirlas y activa el perfil Spring `dev` mediante `SPRING_PROFILES_ACTIVE`, evitando problemas de interpretación de argumentos `-D` en PowerShell.

El backend también importa el `.env` automáticamente al arrancar desde Maven o el IDE, con directorio de trabajo en la raíz del repositorio o en `Backend-web`. Usa valores sin comillas en ese archivo (`OPENAI_API_KEY=...` o `GEMINI_API_KEY=...`); las variables del proceso tienen prioridad. Los perfiles `prod` y `test` no importan el archivo local. Reinicia el backend después de cambiar `.env`.

Detén los contenedores sin borrar el volumen de PostgreSQL con `pnpm stop`.

## Pruebas y compilación

```powershell
pnpm test:all
```

Ejecuta pruebas y build de React, `mvn verify`, `flutter doctor` y, cuando existe una app en `mobile-flutter`, `flutter analyze` y `flutter test`. La integración PostgreSQL usa Testcontainers y se omite explícitamente si Docker no está disponible.

Comandos individuales:

```powershell
pnpm test
pnpm build
cd Backend-web
.\mvnw.cmd verify
```

## Evidencia, operación y contratos

La [matriz de trazabilidad](./docs/traceability.md) separa lo automatizado de los pasos que requieren un proveedor, Docker o un dispositivo físico. Sigue la [validación final y guía de operación](./docs/release-validation.md) para la demostración, despliegue, backups, recuperación y seguridad. El contrato REST está en [docs/openapi.yaml](./docs/openapi.yaml); el modelo de datos y la retención están en [docs/data-model.md](./docs/data-model.md), y el modelo UML en [docs/uml-json-contract.md](./docs/uml-json-contract.md).

## CI/CD y AWS

GitHub Actions instala las dependencias con `pnpm-lock.yaml`, `package-lock.json` y `pubspec.lock`; prueba y compila web, agente local, backend y APK Android, valida las migraciones Flyway contra PostgreSQL, analiza dependencias, construye contenedores y ejecuta `terraform fmt`/`validate`.

Al fusionar un commit aprobado a `main`, el flujo publica `collab-modeler-api:sha-<SHA-completo>`, prepara y conserva el plan de Terraform del ambiente de prueba, aplica ese plan, publica el frontend y ejecuta un smoke test de salud. Producción se promueve con ejecución manual, el SHA completo que pasó prueba y una aprobación del environment `production`; no hay destrucción ni aplicación automática de producción. Consulta la guía inicial, recursos y secretos requeridos en [infra/terraform/README.md](./infra/terraform/README.md).

## Variables de entorno

| Variable | Uso | Predeterminado local |
| --- | --- | --- |
| `DATABASE_URL` | JDBC de PostgreSQL | `jdbc:postgresql://localhost:5433/modeler` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | Credenciales PostgreSQL | `modeler` / `modeler` |
| `REDIS_URL` | Redis | `redis://localhost:6379` |
| `WEBSOCKET_ALLOWED_ORIGINS` | Orígenes permitidos separados por coma | `http://localhost:5173` |
| `PRESENCE_TTL_SECONDS` | Expiración de presencia sin heartbeat | `45` |
| `APP_BOOTSTRAP_ADMIN_EMAIL` | Primera invitación de administrador | sin valor |
| `APP_PUBLIC_URL` | Base de enlaces enviados por correo | `http://localhost:5173` |
| `MAIL_HOST` / `MAIL_PORT` | SMTP de desarrollo | `localhost` / `1025` |
| `SES_SMTP_HOST`, `SES_SMTP_USERNAME`, `SES_SMTP_PASSWORD` | Amazon SES SMTP (`prod`) | obligatorias en producción |
| `OPENAI_API_KEY`, `GEMINI_API_KEY` | Credenciales de los proveedores de IA | sin valor |
| `AI_API_KEY` | Alias heredado de `OPENAI_API_KEY` | sin valor |
| `AI_PROVIDER` | Selección `auto`, `openai` o `gemini`; `auto` prioriza OpenAI | `auto` |
| `AI_BASE_URL`, `AI_TEXT_MODEL`, `AI_VISION_MODEL`, `AI_AUDIO_MODEL` | Configuración de OpenAI | consulta `.env.example` |
| `GEMINI_BASE_URL`, `GEMINI_TEXT_MODEL`, `GEMINI_VISION_MODEL`, `GEMINI_FALLBACK_MODEL` | Configuración de Gemini | consulta `.env.example` |

El perfil `dev` usa autenticación real, PostgreSQL y Mailpit, con cookie HTTP local. `prod` activa cookie `Secure` y Amazon SES SMTP. Configura `APP_BOOTSTRAP_ADMIN_EMAIL`, abre la invitación en Mailpit y completa el registro inicial; el registro público sin invitación está bloqueado.

## Asistente de operaciones

El asistente de texto usa el mismo contrato `DiagramOperation` que el editor manual. El backend interpreta primero con un parser local determinista; solo las instrucciones no reconocidas usan el adaptador configurado por `AI_PROVIDER`. Toda propuesta se valida en seco contra la revisión real del diagrama. Las eliminaciones y los lotes de más de cinco operaciones muestran una previsualización y requieren confirmación.

Las propuestas expiran y no guardan el texto original: se conserva únicamente su hash, la operación validada, autor y proveedor. La aplicación se realiza por el servicio transaccional normal y queda registrada con origen `ASSISTANT`. Sin una clave del proveedor seleccionado, los comandos locales continúan funcionando y los complejos responden 503 sin alterar el modelo.

Para validar el adaptador contra un proveedor real, guarda `OPENAI_API_KEY` o `GEMINI_API_KEY` únicamente en tu `.env` local (nunca en `.env.example` ni en Git), levanta de nuevo el entorno con `pnpm dev` y prueba una instrucción compleja que no reconozca el parser local. `AI_PROVIDER=auto` usa OpenAI cuando ambas claves existen y Gemini cuando solo existe su clave; usa `AI_PROVIDER=gemini` para forzar Gemini. Comprueba primero la previsualización y confirma después la propuesta. Docker Compose transmite la selección, ambas credenciales y los modelos al backend. Una suscripción de ChatGPT no incluye automáticamente crédito de API: la cuenta de API debe tener facturación o crédito disponible.

El parser local admite variantes como `crear clase Factura`, `crea la clase Factura`, `créame una clase Factura` y `añade una clase Factura`. Para un modelo de dominio puede usarse, por ejemplo, `Genera un diagrama breve de base de datos para una farmacia con sus tablas, atributos, relaciones principales y cardinalidades`. El proveedor devuelve operaciones estrictas; el adaptador completa de forma incremental un modelo de 4 a 8 clases y al menos 3 asociaciones cuando una respuesta no cabe en una sola llamada, normaliza alias de tipos habituales y entrega el `BATCH` a la validación normal del backend. El lote se muestra como previsualización y no modifica el diagrama hasta ser confirmado.

En el editor, abre **Asistente** y pulsa el micrófono. Habla y pulsa **Transcribir grabación**; se muestran los estados de permiso, grabación, transcripción y error con reintento. La web graba hasta 15 segundos y envía el audio a `/api/v1/diagrams/{id}/assistant/transcriptions`. La transcripción del servidor requiere OpenAI; con Gemini se usa el reconocimiento del navegador. El texto resultante pasa exactamente por el mismo endpoint de propuestas y validación que una instrucción escrita. El micrófono requiere permiso del navegador.

Para importar una fotografía, un propietario o editor pulsa **Fotografía** en la barra izquierda o **Analizar fotografía** en el panel Asistente y elige PNG, JPEG o WebP de hasta 10 MB. El servidor verifica el formato real y limita ancho, alto y píxeles antes de invocar el adaptador de visión. La respuesta debe contener clases, atributos, relaciones, advertencias y confianza entre 0 y 1. La vista previa permite corregir nombres, tipos y relaciones. Cancelar no modifica el modelo; confirmar lo incorpora como un único `BATCH` y una acción de deshacer revierte toda la importación. El análisis de imagen requiere la clave del proveedor seleccionado.

## Colaboración y trabajo sin conexión

El editor mantiene una outbox de operaciones en el navegador (nunca credenciales ni cookies) y conserva sus `operationId` para reintentos idempotentes. Al reconectar recupera las revisiones faltantes antes de reenviar cambios locales. Si una propiedad cambió en paralelo, muestra opciones para reintentar, descartar o reaplicar.

Los participantes, cursores y selecciones usan Redis y expiran si no llega heartbeat. Los comentarios, respuestas, actividad e hitos se conservan en PostgreSQL. Deshacer/rehacer crea operaciones compensatorias; restaurar un hito crea una revisión nueva y no elimina el historial. Consulta [docs/collaboration.md](./docs/collaboration.md).

## XMI 2.1 y Enterprise Architect

Desde el editor, un propietario o editor puede seleccionar un `.xmi` XMI 2.1 o un `.xml` de exportación nativa de paquete de Enterprise Architect, revisar clases, paquetes, enumeraciones, relaciones y advertencias antes de confirmar. La confirmación reemplaza el contenido del lienzo como un solo lote versionado y puede deshacerse. Todo miembro puede exportar el modelo actual desde **Exportar XMI**. El intercambio conserva semántica UML; los metadatos y el estilo visual propietarios de Enterprise Architect se omiten con una advertencia. Consulta [docs/uml-json-contract.md](./docs/uml-json-contract.md#intercambio-xmi-21).

## Generador de Backend Spring Boot y Especificación Móvil

Desde el panel de **Versiones / Hitos** o desde la barra superior de acciones:
- **Descargar backend (ZIP)**: genera un proyecto Spring Boot 3 completo con Java 21, entidades JPA, herencia `JOINED`, relaciones 1:1, 1:N y N:M con lado propietario, DTOs de entrada y salida con Bean Validation, repositorios, servicios, controladores CRUD completos con actualización, manejo de errores uniforme (`ErrorResponse`), migración inicial Flyway, autenticación completa con JWT y roles `ADMIN`/`USER` en tablas aisladas (`_app_auth_*`), `openapi.yaml`, `Dockerfile` multi-stage, `docker-compose.yml`, trazabilidad `@ModelElement` y `model-traceability.json`, y pruebas con Testcontainers.
- **Descargar spec móvil (JSON)**: emite `modeler-mobile-spec.json` firmado criptográficamente con HMAC-SHA256, vinculado al hash del contrato OpenAPI y la revisión del modelo, para alimentar al generador móvil Flutter local.
- **Validación previa estricta**: el backend valida el modelo antes de generar e informa el UUID específico del elemento afectado si existe alguna clave primaria faltante, tipo inválido, cardinalidad incorrecta o ciclo de herencia.

Endpoints REST disponibles bajo `/api/v1`:
- `GET /api/v1/diagrams/{id}/generation?versionId={versionId}&groupId=com.example&artifactId=mi-app`
- `GET /api/v1/diagrams/{id}/mobile-spec?versionId={versionId}`

## Flutter, Android

1. Instala Flutter estable y ejecuta `flutter doctor -v`.
2. Instala Android Studio o Android SDK Command-line Tools y agrega `platform-tools` a `PATH`.
3. En el movil, habilita opciones de desarrollador pulsando siete veces «Número de compilación».
4. Activa «Depuración USB», conecta un cable de datos, desbloquea el teléfono y acepta la huella RSA.
5. Ejecuta `pnpm mobile:check`. `adb devices -l` debe mostrar el estado `device`, no `unauthorized`.

La aplicación Flutter generada (`mobile-flutter`) implementa arquitectura offline-first con SQLite local (`sqflite`), cola transaccional (outbox) con reintentos e idempotencia (`Idempotency-Key`), refresco automático de tokens JWT en 401 y sincronización bidireccional con pantalla de resolución visual de conflictos (conservar local, descartar local o fusionar manualmente).

### Asistente local: texto, voz y fotografía

- **Texto:** interpreta comandos CRUD en el dispositivo usando el registro de entidades generado desde el mismo diagrama. La propuesta se valida con el mismo esquema de campos y tipos que los formularios.
- **Voz:** usa el servicio de reconocimiento del sistema mediante `speech_to_text`; la transcripción permanece en memoria y pasa por el mismo intérprete local. Para garantizar uso sin red en Android, instala el paquete de español desde **Ajustes → Administración general → Lista de teclados y predeterminado → Entrada de voz → Reconocimiento de voz sin conexión** (la ruta puede variar según la versión de One UI).
- **Fotografía:** `image_picker` captura o elige la imagen y ML Kit reconoce texto latino en el dispositivo. Se inspeccionan los bytes reales y se rechazan archivos vacíos, mayores a 10 MB o con formato ajeno a PNG/JPEG/WebP.
- Crear, editar y eliminar siempre abre una vista previa. Cancelar no llama al repositorio; confirmar escribe el cambio optimista y su operación en la misma transacción SQLite para sincronizarla al volver la conexión.
- El análisis remoto es opcional. Solo se usa al activarlo y con conectividad; cualquier propuesta remota vuelve a validarse localmente y sigue requiriendo confirmación. No se registran imágenes, audio, tokens ni transcripciones.

No es necesario descargar un LLM en el teléfono. El OCR latino se empaqueta con la aplicación. Para voz estrictamente offline sí debe estar instalado el idioma español del reconocedor del dispositivo; si el firmware no ofrece reconocimiento offline, texto y OCR siguen funcionando sin red.

Comandos de verificación y pruebas móviles:
```powershell
cd mobile-flutter
flutter pub get
flutter analyze --no-fatal-infos
flutter test
```

## Agente Local para Generación Flutter en Vivo

El agente local permite generar y ejecutar la aplicación Flutter directamente desde la web de Collab Modeler, sin descargar ZIPs manualmente.

### Instalación del agente

```powershell
pnpm agent:install
```

Esto instala las dependencias de Node.js y compila el agente TypeScript en `mobile-flutter/tools/local-agent/dist/`.

### Uso

1. Inicia el backend y la web: `pnpm dev`
2. Inicia el agente local: `pnpm agent:start`
3. Conecta un dispositivo Android por USB con depuración habilitada.
4. En la web, abre un diagrama y haz clic en **Generar App Móvil** (botón verde en la barra superior).
5. El flujo automático: verifica el agente → obtiene la especificación firmada → genera el proyecto Flutter → lista dispositivos.
6. Selecciona un dispositivo y haz clic en **Ejecutar en dispositivo** o **Compilar APK release**.

### Conexión USB vs Wi-Fi

**USB (recomendado):** El agente ejecuta automáticamente `adb reverse tcp:8080 tcp:8080` antes de `flutter run`. La app Flutter usa `http://localhost:8080` como API base por defecto (`--dart-define=API_BASE_URL=http://localhost:8080`). No requiere Wi-Fi compartida.

**Wi-Fi:** Ingresa la IP privada de la PC (por ejemplo `http://192.168.1.20:8080`) en el campo "API URL" del modal del agente. PC y teléfono deben estar en la misma red. Verifica el firewall.

### Seguridad del agente

- Escucha solo en `127.0.0.1:9876` (loopback, nunca expuesto a la red).
- Valida el origen de cada solicitud (`http://localhost:5173`).
- Verifica la firma HMAC-SHA256 de la especificación.
- Cada especificación incluye un nonce UUID de un solo uso; no se puede reutilizar.
- Solo ejecuta un conjunto cerrado de comandos (`flutter run`, `flutter build`, `adb reverse`, etc.).
- Nunca acepta rutas con `..`, rutas fuera del directorio del usuario ni comandos arbitrarios del servidor.

### Pruebas del agente

```powershell
pnpm agent:test
```

## Generación asíncrona de artefactos

La generación de backend y la especificación móvil se crea con `POST /api/v1/diagrams/{diagramId}/generation-jobs`. Incluye el encabezado `Idempotency-Key` (8–120 caracteres) para que un reintento de red devuelva el mismo trabajo. El trabajo siempre apunta a un `versionId` inmutable; si no se indica, el servidor crea o reutiliza el hito de la revisión actual.

Consulta `GET /api/v1/diagrams/{diagramId}/generation-jobs` o `/{jobId}`. Los estados son `QUEUED`, `RUNNING`, `SUCCEEDED` y `FAILED`; un fallo se reintenta con `POST /{jobId}/retry`. Al terminar, la respuesta entrega enlaces de descarga firmados de cinco minutos para el backend ZIP y `modeler-mobile-spec.json`. El ZIP contiene solo el backend, OpenAPI, README, Docker Compose y trazabilidad. Flutter nunca se incluye en ese ZIP.

En desarrollo, los objetos se cifran con AES-GCM en `GENERATION_LOCAL_STORAGE_PATH`. En AWS, `S3ArtifactStorage` usa SSE-S3 y los trabajos se publican en SQS; Terraform despliega un servicio ECS de workers separado. Configura secretos distintos para `GENERATION_ENCRYPTION_KEY` y `GENERATION_DOWNLOAD_SECRET`.

El agente local solicita `POST /api/v1/diagrams/{diagramId}/generation-jobs/{jobId}/agent-spec` y genera un proyecto Flutter nuevo en `generated-mobile/` desde la especificación firmada. No descarga ni recibe código Flutter del servidor.

## Solución de problemas

- **Corepack no activa pnpm:** abre PowerShell con permisos suficientes una vez o usa `corepack pnpm ...`.
- **Puerto 5432 ocupado:** detén otro PostgreSQL o cambia el mapeo y `DATABASE_URL`.
- **Flyway informa checksum inválido:** no edites V1–V8; crea V9 o posterior para cambios nuevos.
- **Testcontainers no encuentra Docker:** inicia Docker Desktop y espera a que `docker info` responda.
- **ADB muestra `unauthorized`:** revoca autorizaciones USB, reconecta y acepta la huella.
- **ADB no muestra el A56:** cambia cable/puerto, selecciona transferencia de archivos e instala el controlador USB Samsung.
- **El teléfono no llega al backend:** comprueba `ipconfig`, la misma Wi‑Fi, el firewall y `http://IP_DE_LA_PC:8080/actuator/health`.
- **Agente no responde:** verifica que esté corriendo con `pnpm agent:start` y que el puerto 9876 esté libre.
- **Firma inválida:** asegura que la clave de firma del agente coincida con la del backend (`app.mobile-spec.secret`).

Consulta la [hoja de ruta](./docs/product/roadmap.md), la [arquitectura](./docs/architecture.md) y el [contrato UML JSON](./docs/uml-json-contract.md).
