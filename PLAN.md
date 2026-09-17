# Plataforma colaborativa de modelado y generación de backend y móvil

## Resumen

Construir una aplicación web colaborativa para que ingenieros diseñen modelos de datos mediante diagramas de clases. Admitirá edición manual, texto, voz y fotografías; colaboración en tiempo real; comentarios e historial; intercambio XMI con Enterprise Architect; generación de un backend Spring Boot descargable y generación local de una aplicación Flutter funcional para Android.

El sistema sanitario descrito es únicamente el contexto motivador. La aplicación Flutter sí forma parte del producto: se generará localmente desde el mismo hito inmutable del diagrama que el backend, se instalará en un Samsung A56 físico y consumirá su API.

## Implementación

- Crear un monorepo con:
  - Cliente React + TypeScript, Vite y React Flow.
  - API Spring Boot estable con Java 21 y Maven.
  - Generador de backend Spring Boot, especificaciones móviles y un agente local con plantillas Flutter para Android.
  - PostgreSQL para datos persistentes.
  - Redis para presencia y distribución de eventos WebSocket.
  - Terraform y contenedores para AWS.
- Implementar cuentas de usuario para la web con dos métodos de acceso unificados: OAuth 2.0/OpenID Connect con Google y correo/contraseña. Google solo aceptará correos verificados y se vinculará a la cuenta local existente cuando coincida el correo.
  - El registro por correo solo estará disponible desde un enlace compartido o invitación válida; solicitará nombre completo, correo, contraseña y confirmación, y exigirá verificación de correo antes de dar acceso.
  - Se admitirán recuperación y restablecimiento de contraseña mediante enlaces de un solo uso con expiración. Las contraseñas usarán hash Argon2id y las sesiones web usarán cookies `HttpOnly`, `Secure` y protección CSRF.
  - El correo definido en `APP_BOOTSTRAP_ADMIN_EMAIL` recibirá una invitación inicial y, al completar el alta, será administrador de la plataforma. El creador de cada diagrama será propietario y podrá activar, rotar o revocar el enlace compartido.
  - Los permisos serán por diagrama: `OWNER`, `EDITOR`, `READER` y solicitud `PENDING`. Un invitado autenticado crea una solicitud pendiente; el propietario la aprueba como lector o editor, puede cambiar el rol y revocar el acceso.
  - Producción enviará correos mediante Amazon SES; desarrollo y pruebas usarán Mailpit. Los tokens de verificación, recuperación e invitación se almacenarán solo como hash.
- Construir el lienzo UML con clases, atributos tipados, claves, restricciones, enumeraciones, asociaciones, cardinalidades, herencia, movimiento, selección múltiple, deshacer y restauración de versiones.
- Mantener identidad visual de participantes, cursores, selección, actividad y comentarios generales o anclados a clases, atributos y relaciones.
- Sincronizar mediante operaciones optimistas versionadas:
  - Cambios sobre elementos diferentes se integran automáticamente.
  - Cambios incompatibles sobre la misma propiedad se rechazan y muestran un comparador.
  - Restaurar una versión crea una nueva revisión sin borrar el historial.
  - La reconexión recupera operaciones faltantes y reconcilia cambios locales.
- Integrar proveedores configurables de IA mediante adaptadores:
  - Texto y voz se convierten en operaciones estructuradas del mismo dominio utilizado por el editor manual.
  - Las operaciones válidas se aplican inmediatamente y pueden deshacerse.
  - Eliminaciones y cambios masivos requieren confirmación.
  - La IA nunca modifica directamente la base de datos ni devuelve código ejecutable.
- Incorporar un asistente educativo web separado del asistente de operaciones:
  - Responderá preguntas sobre tablas, atributos, cardinalidades, relaciones y uso de la interfaz mediante IA contextual.
  - Recibirá solo el contexto mínimo necesario del diagrama y no emitirá operaciones ni modificará persistencia.
- Procesar fotografías mediante visión:
  - Extraer una propuesta de clases, atributos y relaciones.
  - Mostrar advertencias y una previsualización antes de incorporarla.
  - Tras confirmar, agregarla como un lote versionado y permitir su corrección normal.
- Importar y exportar el modelo semántico mediante XMI 2.1, formato soportado para intercambio UML por Enterprise Architect. Se admitirán paquetes, clases, propiedades, asociaciones, cardinalidades, enumeraciones y generalizaciones; los elementos no soportados producirán advertencias.
- Generar desde un hito inmutable del diagrama un ZIP con:
  - Spring Boot, Java 21 y Maven.
  - Entidades JPA, repositorios, servicios, DTO de entrada/salida, mapeadores y controladores REST CRUD.
  - PostgreSQL, Flyway, Bean Validation, manejo uniforme de errores y OpenAPI.
  - Dockerfile, Docker Compose, configuración por variables y pruebas iniciales.
  - Relaciones JPA para 1:1, 1:N y N:M, enumeraciones almacenadas como texto y herencia `JOINED`.
  - Anotaciones con el UUID del elemento de origen y un `model-traceability.json` que relacione cada elemento con archivo y líneas generadas.
  - Registro por correo y contraseña, JWT de acceso y refresco, y Google OAuth opcional para el backend y la aplicación generados. El primer usuario registrado será administrador y los siguientes serán usuarios estándar.
- Generar por separado un `modeler-mobile-spec.json` firmado, ligado a la misma revisión inmutable y a `openapi.yaml`. El archivo no contendrá código Flutter.
- Mediante un agente local instalado en la computadora, generar el proyecto Flutter con modelos, cliente OpenAPI tipado, CRUD adaptable, autenticación, SQLite, cola offline, sincronización bidireccional, resolución de conflictos y asistente móvil para texto, voz y OCR.
- El agente compilará un APK e instalará o ejecutará la aplicación en un Samsung A56 físico mediante ADB. El móvil se conectará al backend de la computadora usando su IP local, no `localhost`.
- Validar antes de generar: nombres, tipos, clave primaria, cardinalidades, lado propietario, herencia y referencias. Los errores bloquean la descarga y señalan el elemento afectado.
- Desplegar en AWS mediante Terraform:
  - S3 + CloudFront para el cliente.
  - ECS Fargate, ALB y ECR para API y trabajadores.
  - RDS PostgreSQL, ElastiCache Redis, S3 para imágenes/ZIP y SQS para generación.
  - Secrets Manager, ACM, HTTPS, CloudWatch y ambientes separados de prueba y producción.

## Interfaces y modelo público

- Definir `DiagramModel` con `ClassElement`, `Attribute`, `Enumeration`, `Association`, `Generalization`, posición y metadatos estables.
- Definir `DiagramOperation` como unión discriminada con `operationId`, `baseRevision`, `elementVersion`, autor, tipo y payload.
- Exponer REST para sesión actual, inicio de sesión, registro invitado, verificación de correo, recuperación y restablecimiento de contraseña, proyectos, solicitudes de acceso, asignación de rol por diagrama, enlaces compartidos, instantáneas, versiones, comentarios, importación XMI, análisis de imagen, asistente educativo y generación de ZIP de backend.
- Exponer WebSocket por diagrama para operaciones aceptadas/rechazadas, presencia, cursores y recuperación desde una revisión.
- Exponer el asistente como comandos estructurados de creación, modificación, movimiento, relación y eliminación.
- Exponer el asistente educativo como consulta de solo lectura, separado de los comandos estructurados.
- Mantener los contratos REST en OpenAPI y versionarlos bajo `/api/v1`.
- Exponer una especificación móvil firmada por revisión para que el agente local genere Flutter desde OpenAPI y contratos de sincronización.

## Pruebas y aceptación

- Comprobar edición manual completa y persistencia después de recargar.
- Abrir varias sesiones autenticadas y verificar convergencia, presencia, autoría, reconexión y resolución de conflictos simultáneos.
- Validar inicio con Google y correo/contraseña, nombre completo obligatorio, verificación de correo, recuperación de contraseña, vinculación de identidades con el mismo correo y prohibición de registro sin invitación.
- Validar enlace compartido, rotación, revocación, solicitud pendiente y asignación de lector/editor por el propietario; un lector no podrá modificar y un usuario pendiente no podrá leer el diagrama.
- Probar comandos equivalentes mediante texto y voz, confirmación de acciones destructivas y deshacer.
- Verificar que una fotografía no altera el modelo hasta confirmar su propuesta.
- Realizar round-trip con archivos XMI exportados desde Enterprise Architect y comprobar clases, atributos, relaciones, cardinalidades y herencia.
- Generar modelos de ventas, colegio y salud; compilar el backend ZIP, ejecutar sus pruebas y consumir sus CRUD contra PostgreSQL con Testcontainers.
- Generar Flutter localmente desde la especificación de la misma revisión, compilar un APK, instalarlo en un Samsung A56 y consumir el backend local mediante la IP de la computadora.
- Verificar registro, inicio de sesión, refresco de token y Google OAuth opcional en una solución generada.
- Crear, editar y eliminar datos en Flutter sin conexión; recuperar red, sincronizar sin duplicados y resolver conflictos visibles.
- Verificar comandos de texto, voz y OCR local en la aplicación móvil sin conexión, seguidos de sincronización al recuperar red.
- Confirmar que cada entidad, campo y relación generada conserva trazabilidad hacia el modelo.
- Ejecutar pruebas de autorización, carga maliciosa, XXE, tamaño de archivos, WebSocket, CSRF y manejo seguro de secretos.
- Validar Terraform, desplegar el ambiente de prueba y ejecutar una prueba integral desde Google OAuth hasta la descarga, ejecución del backend y uso de la aplicación Flutter generada localmente e instalada en el Samsung A56.

## Supuestos y límites

- La interfaz inicial estará en español y preparada para internacionalización.
- Google y correo/contraseña representan una única cuenta cuando el proveedor confirma el mismo correo verificado; no se crearán cuentas duplicadas por método de acceso.
- El registro público no estará disponible. Un usuario nuevo solo puede darse de alta desde una invitación válida o desde la invitación inicial del administrador configurado.
- Se usará únicamente el núcleo MIT de React Flow; la colaboración será propia y no dependerá de ejemplos comerciales.
- Se fijará una versión estable de Spring Boot al implementar; no se usarán versiones milestone o snapshot.
- XMI garantiza intercambio semántico, pero no reproducción exacta del diseño visual ni extensiones propietarias de Enterprise Architect.
- No se leerán archivos EAP, EAPX o QEA directamente.
- El ZIP se regenera desde una versión del modelo; la plataforma no fusionará modificaciones manuales realizadas posteriormente sobre el código descargado.
- El ZIP contiene únicamente el backend. Flutter se genera fuera del ZIP mediante un agente local instalado en la computadora; no se infieren flujos de negocio específicos a partir de un diagrama de datos, sino una interfaz CRUD adaptable que puede personalizarse después.
- El Samsung A56 es el dispositivo objetivo inicial. El desarrollo requiere Flutter SDK, Android SDK, ADB, depuración USB y conexión del móvil y la computadora a la misma red Wi-Fi; el backend se configura con la IP local de la computadora.
- La IA móvil funciona offline para texto, voz y OCR; el análisis visual semántico complejo requiere conectividad o un modelo local adicional configurado explícitamente.
- No habrá un SLA numérico de concurrencia inicial; sí se verificarán consistencia y recuperación bajo múltiples sesiones simultáneas.
