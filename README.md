# Collab Modeler

Editor UML colaborativo con React/TypeScript, Spring Boot/Java 21, PostgreSQL y Redis.

## Requisitos

- Node.js 22 o superior y Corepack.
- Java JDK 21. No hace falta instalar Maven: `backend_parcial/mvnw.cmd` (Windows) y `backend_parcial/mvnw` fijan Maven 3.9.11.
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

Detén los contenedores sin borrar el volumen de PostgreSQL con `pnpm stop`.

## Pruebas y compilación

```powershell
pnpm test:all
```

Ejecuta pruebas y build de React, `mvn verify`, `flutter doctor` y, cuando existe una app en `mobile_parcial`, `flutter analyze` y `flutter test`. La integración PostgreSQL usa Testcontainers y se omite explícitamente si Docker no está disponible.

Comandos individuales:

```powershell
pnpm test
pnpm build
cd backend_parcial
.\mvnw.cmd verify
```

## Variables de entorno

| Variable | Uso | Predeterminado local |
| --- | --- | --- |
| `DATABASE_URL` | JDBC de PostgreSQL | `jdbc:postgresql://localhost:5433/modeler` |
| `DATABASE_USER` / `DATABASE_PASSWORD` | Credenciales PostgreSQL | `modeler` / `modeler` |
| `REDIS_URL` | Redis | `redis://localhost:6379` |
| `WEBSOCKET_ALLOWED_ORIGINS` | Orígenes permitidos separados por coma | `http://localhost:5173` |
| `PRESENCE_TTL_SECONDS` | Expiración de presencia sin heartbeat | `45` |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | OAuth fuera de `dev` | sin valor |
| `APP_BOOTSTRAP_ADMIN_EMAIL` | Primera invitación de administrador | sin valor |
| `APP_PUBLIC_URL` | Base de enlaces enviados por correo | `http://localhost:5173` |
| `MAIL_HOST` / `MAIL_PORT` | SMTP de desarrollo | `localhost` / `1025` |
| `SES_SMTP_HOST`, `SES_SMTP_USERNAME`, `SES_SMTP_PASSWORD` | Amazon SES SMTP (`prod`) | obligatorias en producción |
| `AI_API_KEY` | Proveedor de IA | sin valor |
| `AI_PROVIDER` | Adaptador de texto (`openai`) | `openai` |
| `AI_BASE_URL`, `AI_TEXT_MODEL`, `AI_VISION_MODEL` | Adaptador de IA | consulta `.env.example` |

El perfil `dev` usa autenticación real, PostgreSQL y Mailpit, con cookie HTTP local. `prod` activa cookie `Secure`, Google OIDC y Amazon SES SMTP. Configura `APP_BOOTSTRAP_ADMIN_EMAIL`, abre la invitación en Mailpit y completa el registro inicial; el registro público sin invitación está bloqueado.

## Asistente de operaciones

El asistente de texto usa el mismo contrato `DiagramOperation` que el editor manual. El backend interpreta primero con un parser local determinista; solo las instrucciones no reconocidas usan el adaptador configurado por `AI_PROVIDER`. Toda propuesta se valida en seco contra la revisión real del diagrama. Las eliminaciones y los lotes de más de cinco operaciones muestran una previsualización y requieren confirmación.

Las propuestas expiran y no guardan el texto original: se conserva únicamente su hash, la operación validada, autor y proveedor. La aplicación se realiza por el servicio transaccional normal y queda registrada con origen `ASSISTANT`. Sin `AI_API_KEY`, los comandos locales continúan funcionando y los complejos responden 503 sin alterar el modelo.

Para validar el adaptador contra el proveedor real, guarda `AI_API_KEY` únicamente en tu `.env` local (nunca en `.env.example` ni en Git), levanta de nuevo el entorno con `pnpm dev` y prueba una instrucción compleja que no reconozca el parser local. Comprueba primero la previsualización y confirma después la propuesta. Docker Compose transmite `AI_PROVIDER`, `AI_BASE_URL`, `AI_API_KEY`, `AI_TEXT_MODEL` y `AI_VISION_MODEL` al backend. Una suscripción de ChatGPT no incluye automáticamente crédito de API: la cuenta de API debe tener facturación o crédito disponible.

El parser local admite variantes como `crear clase Factura`, `crea la clase Factura`, `créame una clase Factura` y `añade una clase Factura`. Para un modelo de dominio puede usarse, por ejemplo, `Genera un diagrama breve de base de datos para una farmacia con sus tablas, atributos, relaciones principales y cardinalidades`. El proveedor devuelve operaciones estrictas; el adaptador completa de forma incremental un modelo de 4 a 8 clases y al menos 3 asociaciones cuando una respuesta no cabe en una sola llamada, normaliza alias de tipos habituales y entrega el `BATCH` a la validación normal del backend. El lote se muestra como previsualización y no modifica el diagrama hasta ser confirmado.

## Colaboración y trabajo sin conexión

El editor mantiene una outbox de operaciones en el navegador (nunca credenciales ni cookies) y conserva sus `operationId` para reintentos idempotentes. Al reconectar recupera las revisiones faltantes antes de reenviar cambios locales. Si una propiedad cambió en paralelo, muestra opciones para reintentar, descartar o reaplicar.

Los participantes, cursores y selecciones usan Redis y expiran si no llega heartbeat. Los comentarios, respuestas, actividad e hitos se conservan en PostgreSQL. Deshacer/rehacer crea operaciones compensatorias; restaurar un hito crea una revisión nueva y no elimina el historial. Consulta [docs/collaboration.md](./docs/collaboration.md).

## XMI 2.1 y Enterprise Architect

Desde el editor, un propietario o editor puede seleccionar un `.xmi` XMI 2.1 o un `.xml` de exportación nativa de paquete de Enterprise Architect, revisar clases, paquetes, enumeraciones, relaciones y advertencias antes de confirmar. La confirmación reemplaza el contenido del lienzo como un solo lote versionado y puede deshacerse. Todo miembro puede exportar el modelo actual desde **Exportar XMI**. El intercambio conserva semántica UML; los metadatos y el estilo visual propietarios de Enterprise Architect se omiten con una advertencia. Consulta [docs/uml-json-contract.md](./docs/uml-json-contract.md#intercambio-xmi-21).

## Flutter, Android

1. Instala Flutter estable y ejecuta `flutter doctor -v`.
2. Instala Android Studio o Android SDK Command-line Tools y agrega `platform-tools` a `PATH`.
3. En el movil, habilita opciones de desarrollador pulsando siete veces «Número de compilación».
4. Activa «Depuración USB», conecta un cable de datos, desbloquea el teléfono y acepta la huella RSA.
5. Ejecuta `pnpm mobile:check`. `adb devices -l` debe mostrar el estado `device`, no `unauthorized`.

La futura aplicación Flutter generada debe usar la IP privada de la PC (por ejemplo `http://192.168.1.20:8080`), nunca `localhost`. PC y teléfono deben compartir Wi‑Fi y el Firewall de Windows debe permitir el puerto 8080 solo en la red privada.

## Solución de problemas

- **Corepack no activa pnpm:** abre PowerShell con permisos suficientes una vez o usa `corepack pnpm ...`.
- **Puerto 5432 ocupado:** detén otro PostgreSQL o cambia el mapeo y `DATABASE_URL`.
- **Flyway informa checksum inválido:** no edites V1–V4; crea V5 o posterior para cambios nuevos.
- **Testcontainers no encuentra Docker:** inicia Docker Desktop y espera a que `docker info` responda.
- **ADB muestra `unauthorized`:** revoca autorizaciones USB, reconecta y acepta la huella.
- **ADB no muestra el A56:** cambia cable/puerto, selecciona transferencia de archivos e instala el controlador USB Samsung.
- **El teléfono no llega al backend:** comprueba `ipconfig`, la misma Wi‑Fi, el firewall y `http://IP_DE_LA_PC:8080/actuator/health`.

Consulta [PLAN.md](./PLAN.md), [arquitectura](./docs/architecture.md) y el [contrato UML JSON](./docs/uml-json-contract.md).
