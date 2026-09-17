# Prompts de desarrollo de Collab Modeler

Estos prompts deben ejecutarse en orden. Cada uno representa un incremento que debe quedar probado y funcional antes de continuar con el siguiente. No se debe intentar implementar toda la hoja de ruta en una sola ejecución.

## Instrucción base para todos los prompts

Copiar este bloque al inicio de cada prompt:

```text
Trabaja sobre el monorepo Collab Modeler existente. Antes de modificar archivos, revisa PLAN.md, README.md, docs/architecture.md y el código relacionado con el objetivo. Conserva las funcionalidades existentes y no reemplaces implementaciones reales por mocks o placeholders.

Implementa el objetivo de extremo a extremo: modelo de datos, backend, frontend web, aplicación Flutter y documentación cuando correspondan. Respeta los contratos bajo /api/v1, utiliza migraciones Flyway incrementales y no modifiques migraciones que ya pudieron ejecutarse. Mantén compatibilidad con Java 21, Spring Boot, React, TypeScript, Flutter/Dart y PostgreSQL.

Agrega pruebas unitarias y de integración para los casos normales, errores, autorización y concurrencia relevantes. Ejecuta compilación, pruebas y análisis estático disponibles. Corrige los errores encontrados antes de terminar. Al finalizar, informa archivos cambiados, decisiones tomadas, comandos ejecutados, resultados de pruebas y cualquier riesgo pendiente. No declares terminado algo que no hayas verificado.
```

---## 01. Entorno reproducible y línea base

```text
Objetivo: hacer que Collab Modeler pueda instalarse, compilarse, probarse y ejecutarse de manera reproducible en una computadora nueva.

Revisa las versiones actuales y agrega Maven Wrapper al backend y una forma documentada de habilitar pnpm mediante Corepack y Flutter SDK. Verifica los scripts raíz para desarrollo, build y pruebas de web, backend y Flutter. Completa .gitignore para excluir node_modules, dist, target, .dart_tool, build de Flutter, secretos, archivos tfstate y configuración local. Revisa Dockerfiles y docker-compose.yml sin eliminar los servicios existentes.

Crea comandos claros para: instalar dependencias, ejecutar frontend y backend, levantar PostgreSQL y Redis, validar Flutter, detectar un dispositivo Android por ADB, ejecutar todas las pruebas y detener el entorno. Añade health checks donde sean necesarios. Actualiza README.md con requisitos, variables de entorno y solución de problemas frecuentes, incluyendo Flutter SDK, Android SDK, ADB y depuración USB para el Samsung A56.

Criterio de aceptación: desde una copia limpia se puede levantar el entorno, abrir el frontend, consultar la salud del backend, validar Flutter y ejecutar todas las pruebas disponibles con comandos documentados.
```

---## 02. PostgreSQL, Flyway y persistencia completa

```text
Objetivo: dejar operativa y comprobada la base de datos PostgreSQL de Collab Modeler.

Audita las entidades, repositorios y migraciones Flyway existentes para diagramas, operaciones, miembros, comentarios y versiones. Corrige inconsistencias entre JPA y SQL mediante una nueva migración, sin reescribir migraciones existentes. Define claves primarias, claves foráneas, índices, restricciones únicas, borrado referencial y tipos adecuados. Asegura que los tokens compartidos se almacenen únicamente como hash.

Configura perfiles dev, test y producción. Dev debe usar PostgreSQL real; test puede usar Testcontainers PostgreSQL para comprobar compatibilidad real y no depender solo de H2. Agrega pruebas de repositorio y migración. Verifica que un diagrama, sus operaciones, miembros, comentarios y versiones sobrevivan al reinicio del backend.

Criterio de aceptación: una base vacía se migra automáticamente, las restricciones se cumplen y una prueba de persistencia con PostgreSQL pasa de extremo a extremo.
```

--## 03. Modelo UML semántico completo

```text
Objetivo: completar el dominio compartido que representa el diagrama UML.

Amplía el modelo frontend y backend para soportar clases, atributos, enumeraciones, valores de enumeración, asociaciones, cardinalidades, roles de asociación, lado propietario y generalizaciones/herencia. Mantén UUID estables y una versión por elemento. Define claramente nulabilidad, unicidad, clave primaria y tipos escalares permitidos.

Evita duplicar reglas incompatibles entre TypeScript y Java. Documenta el contrato JSON y agrega ejemplos. Si el cambio requiere transformar datos ya persistidos, crea una migración o estrategia de compatibilidad. Agrega pruebas de serialización y deserialización.

Criterio de aceptación: cualquier modelo descrito en PLAN.md puede representarse sin perder información al guardarlo y volverlo a cargar.
```

--## 04. Operaciones, validación e idempotencia

```text
Objetivo: implementar todas las operaciones versionadas del editor de forma segura.

Completa DiagramOperation y DiagramService con crear, renombrar, mover y eliminar clases; crear, editar, reordenar y eliminar atributos; crear, editar y eliminar asociaciones; crear, editar y eliminar enumeraciones; y crear o eliminar generalizaciones. Añade una operación BATCH atómica para importaciones y acciones de IA.

Valida operationId, baseRevision y expectedElementVersion en todas las operaciones que modifican elementos, incluido CLASS_MOVED. Rechaza referencias inexistentes, nombres inválidos, duplicados, ciclos de herencia y cardinalidades inválidas. Conserva idempotencia ante reintentos. Devuelve errores estructurados con código, revisión actual, elemento afectado y datos suficientes para resolver conflictos.

Criterio de aceptación: operaciones sobre elementos diferentes convergen, cambios incompatibles sobre la misma propiedad producen 409 y repetir un operationId nunca aplica el cambio dos veces.
```

--## 05. Editor visual UML completo

```text
Objetivo: convertir el frontend actual en un editor UML completamente utilizable.

Implementa un panel de propiedades para crear, editar y eliminar clases, atributos, enumeraciones, asociaciones y herencia. Permite configurar tipo, clave primaria, requerido, único, cardinalidades, nombre de relación y lado propietario. Añade selección múltiple, movimiento múltiple, eliminación con confirmación, atajos de teclado y mensajes de validación junto al elemento afectado.

Mantén React Flow y Zustand. Evita mutaciones directas del estado. Garantiza que cada acción manual genere una DiagramOperation válida y que los nodos y enlaces representen visualmente asociaciones, cardinalidades y herencia. Añade pruebas de componentes y del store.

Criterio de aceptación: un usuario puede construir desde cero modelos de ventas, colegio y salud exclusivamente desde la interfaz y recargarlos sin pérdida de información.


--## 06. Identidad, autenticación y acceso por invitación

```text
Objetivo: implementar cuentas de usuario unificadas, seguras y compatibles con Google y correo/contraseña.

Modela cuentas con correo normalizado, nombre completo, estado de verificación y sujeto estable para permisos; separa las identidades Google y contraseña local. Agrega migraciones incrementales para cuentas, identidades, invitaciones, verificaciones de correo y recuperación de contraseña. Conserva las membresías de diagramas existentes y migra o vincula sus sujetos sin perder acceso.

Integra OAuth 2.0/OpenID Connect con Google y acepta únicamente correos verificados. Implementa registro por correo y contraseña solo desde una invitación válida; exige nombre completo, correo, contraseña y confirmación. Si Google confirma el mismo correo de una cuenta local, vincula ambas identidades a una sola cuenta. Implementa verificación de correo, recuperación y restablecimiento con tokens de un solo uso, expiración y almacenamiento exclusivamente como hash.

Usa Argon2id para contraseñas, sesiones de navegador con cookies HttpOnly/Secure/SameSite y CSRF, límites de intentos y respuestas que no permitan enumerar correos. Configura Amazon SES para producción y Mailpit para desarrollo/pruebas. Al iniciar una instalación, genera una invitación para APP_BOOTSTRAP_ADMIN_EMAIL; al completar el alta, esa cuenta es administradora de la plataforma. Mantén un perfil dev aislado y explícito.

Expón GET /api/v1/auth/me y endpoints JSON de login, registro invitado, verificación de correo, solicitud/restablecimiento de contraseña y logout. Conserva el flujo /join/{token}: tras autenticarse y verificarse, el usuario queda como EDITOR. Solo el propietario puede administrar enlaces. Define respuestas 401, 403, 404 y 409 coherentes.

Criterio de aceptación: Google y correo/contraseña acceden a una única cuenta por correo verificado; no se puede registrar sin invitación; las cuentas no verificadas no acceden al editor; recuperación, rotación y revocación funcionan; y un usuario no miembro no puede leer, modificar, exportar ni generar el proyecto.
```

--## 07. Pantalla de inicio de sesión, registro y proyectos

```text
Objetivo: entregar una experiencia web clara para entrar, registrarse desde una invitación y administrar proyectos.

Implementa un AuthGate que consulte /api/v1/auth/me antes de cargar el editor. Crea una pantalla pública en español con botón “Continuar con Google”, formulario de correo y contraseña, cierre de sesión y mensajes accesibles de error y carga. No almacenes credenciales ni tokens de sesión en localStorage.

Al abrir una invitación válida, conserva el destino mientras se completa la autenticación. Muestra registro únicamente en ese contexto e incluye campos obligatorios de nombre completo, correo, contraseña y confirmación. Implementa pantallas para verificar correo, solicitar recuperación y restablecer contraseña. Tras completar el flujo, redirige al diagrama invitado; si no hay invitación, dirige al listado de proyectos propios y compartidos.

Integra la interfaz para crear, abrir, rotar y revocar enlaces compartidos, y muestra el nombre real del usuario autenticado en vez de datos simulados. Trata 401, 403, 404, 409 y errores de red con mensajes seguros y accionables.

Criterio de aceptación: una persona invitada puede registrarse con nombre completo, verificar correo, entrar y editar el diagrama; puede iniciar luego con Google o contraseña usando la misma cuenta; una persona sin invitación no ve un formulario de registro ni puede abrir el editor.
```

--## 08. Colaboración, presencia y seguridad WebSocket

```text
Objetivo: implementar colaboración real y autorizada en múltiples sesiones.

Protege el handshake y las suscripciones STOMP. Un usuario solo debe poder suscribirse a diagramas de los que sea miembro. Elimina allowedOriginPatterns("*") en producción y configura orígenes permitidos. Implementa presencia, ingreso/salida, cursor, selección y actividad reciente con identidad real del usuario.

Usa Redis para presencia efímera y fan-out entre varias instancias del backend. Define expiración y heartbeat para evitar usuarios fantasma. No persistas cada movimiento de cursor en PostgreSQL. Sustituye los avatares y el contador simulados del frontend por información real.

Criterio de aceptación: dos navegadores muestran cambios, cursores y participantes en tiempo real; un usuario no autorizado no puede escuchar tópicos ajenos; dos instancias del backend convergen mediante Redis.
```

--## 09. Reconexión, cola offline y conflictos

```text
Objetivo: evitar pérdida silenciosa de cambios cuando la red falla o existen ediciones simultáneas.

Implementa en el frontend una cola persistente de operaciones pendientes, estados pending/acknowledged/rejected y reintentos idempotentes. Al reconectar, solicita operaciones posteriores a la última revisión conocida, aplica cambios remotos en orden y luego reenvía operaciones locales compatibles.

No reemplaces ciegamente el estado optimista con una instantánea. Implementa una interfaz de conflicto que compare valor local y valor del servidor y permita descartar, reintentar o reaplicar el cambio. Documenta la estrategia de convergencia y agrega pruebas con desconexión y orden alterado de respuestas.

Criterio de aceptación: editar durante una interrupción temporal y reconectar no pierde cambios; los conflictos quedan visibles y nunca se resuelven silenciosamente.
```

--## 10. Deshacer, rehacer, hitos y restauración

```text
Objetivo: completar el historial del usuario y las versiones persistentes del diagrama.

Implementa undo y redo mediante operaciones compensatorias, evitando reemplazar arbitrariamente todo el documento desde el cliente. Distingue historial local de hitos persistentes. Integra las API existentes para crear, listar y restaurar versiones con nombre, autor, fecha y revisión.

Restaurar una versión debe crear una nueva revisión y conservar todo el historial. Difunde la restauración por WebSocket y controla conflictos con operaciones pendientes. Añade una interfaz para previsualizar y confirmar la restauración.

Criterio de aceptación: deshacer y rehacer funcionan después de varias operaciones; restaurar un hito no borra versiones ni operaciones anteriores y todos los clientes convergen.
```

--## 11. Comentarios y actividad colaborativa

```text
Objetivo: integrar comentarios generales y comentarios anclados a elementos del modelo.

Conecta el frontend con la API de comentarios. Permite comentar el diagrama completo o una clase, atributo, asociación, enumeración o generalización. Muestra autor, fecha, estado resuelto y contexto del elemento. Permite responder o, si el modelo actual no soporta hilos, extiéndelo mediante una migración.

Autoriza creación, lectura y resolución según la membresía. Difunde cambios por WebSocket y registra actividad relevante sin mezclarla con presencia efímera.

Criterio de aceptación: dos participantes pueden comentar y resolver conversaciones en tiempo real, y los comentarios permanecen tras reiniciar la aplicación.
```

## 12. Importación y exportación XMI 2.1

```text
Objetivo: completar un intercambio XMI seguro y compatible con Enterprise Architect.

Amplía XmiService para paquetes, clases, propiedades, tipos, enumeraciones, asociaciones, roles, cardinalidades y generalizaciones. Mantén protección contra XXE, límites de tamaño y validación de contenido. Devuelve advertencias estructuradas para elementos no soportados.

Implementa en el frontend selección de archivo, vista previa, advertencias y confirmación. La importación confirmada debe aplicarse como un único BATCH versionado y poder deshacerse. Añade exportación desde la interfaz.

Criterio de aceptación: realiza pruebas round-trip con varios archivos XMI representativos de Enterprise Architect y compara semánticamente el modelo original con el reimportado.
```

## 13. Asistente de texto y arquitectura de proveedores de IA

```text
Objetivo: transformar instrucciones naturales en operaciones estructuradas seguras.

Define una interfaz de proveedor configurable y separa interpretación de comandos, validación y aplicación. El proveedor debe devolver únicamente un esquema JSON estricto de DiagramOperation o BATCH; nunca SQL ni código ejecutable. Conserva un parser local determinista para instrucciones simples y usa IA solo cuando sea necesario.

Implementa creación, modificación, relaciones, enumeraciones, herencia, movimiento y eliminación. Muestra una previsualización para acciones destructivas o masivas. Valida siempre en el backend y registra autoría y proveedor sin almacenar secretos ni contenido sensible innecesario.

Criterio de aceptación: instrucciones equivalentes manuales y textuales producen las mismas operaciones; una respuesta inválida del proveedor no modifica el diagrama.
```

## 14. Voz e imágenes

```text
Objetivo: completar las entradas por voz y fotografías sin comprometer la integridad del modelo.

Para voz, implementa estados de permiso, grabación, transcripción, error y reintento; la transcripción debe recorrer exactamente el mismo flujo seguro del asistente textual. Para imágenes, valida MIME real, tamaño y dimensiones, procesa mediante el adaptador de visión y devuelve una propuesta estructurada con advertencias y nivel de confianza.

Mejora la vista previa para permitir corregir clases, atributos y relaciones antes de confirmar. La confirmación debe aplicar un solo BATCH atómico; cancelar no debe producir ninguna operación.

Criterio de aceptación: voz y texto equivalentes producen el mismo resultado; una fotografía nunca modifica el modelo antes de la confirmación y la importación completa puede deshacerse en una acción.
```

## 15. Generador Spring Boot completo

```text
Objetivo: generar un backend Spring Boot ejecutable y trazable desde un hito inmutable.

Completa BackendGenerator para producir entidades JPA, relaciones 1:1, 1:N y N:M con lado propietario, enumeraciones como STRING y herencia JOINED. Genera DTO separados de entrada y salida, Bean Validation, mapeadores, repositorios, servicios, CRUD completo con actualización, controladores REST, errores uniformes y OpenAPI como contrato fuente de la aplicación Flutter.

Incluye PostgreSQL, Flyway, configuración por variables, Dockerfile multi-stage, Docker Compose, README y pruebas. Incluye autenticación con registro por correo y contraseña, JWT de acceso y refresco, roles ADMIN y USER, y Google OAuth opcional por variables de entorno. El primer usuario registrado debe obtener ADMIN. Aísla las tablas y nombres de autenticación para evitar colisiones con entidades generadas desde el diagrama.

Valida antes de generar nombres, tipos, claves primarias, cardinalidades, referencias, propiedad de relaciones y ciclos. Los errores deben incluir el UUID del elemento afectado. Mantén @ModelElement y amplía model-traceability.json a clases, campos y relaciones con archivo y líneas.

Genera desde una versión guardada, no desde un documento mutable sin identificar. El ZIP debe contener solo el backend, OpenAPI, Docker Compose, README y trazabilidad; no debe contener código Flutter. Emite por separado un modeler-mobile-spec.json firmado, ligado a la misma revisión y a openapi.yaml, para que un agente local genere el móvil. Protege nombres de ZIP y rutas contra path traversal.

Criterio de aceptación: los backends de modelos de ventas, colegio y salud compilan, ejecutan sus pruebas, autentican usuarios, publican OpenAPI y permiten consumir todos sus CRUD contra PostgreSQL mediante Testcontainers.
```

## 16. Plantilla y generador Flutter local para Android

```text
Objetivo: crear la plantilla y el generador Flutter que producirá localmente una aplicación Android desde modeler-mobile-spec.json y el contrato OpenAPI de la misma revisión.

Crea una plantilla Flutter mantenible, organizada por capas y sin código de ejemplo estático. Desde modeler-mobile-spec.json y OpenAPI, produce modelos tipados, cliente HTTP autenticado, repositorios, gestión de estado, rutas, listas paginadas, búsqueda, vista de detalle, formularios de alta y edición, eliminación confirmada, validación y selección de relaciones para cada entidad. Soporta enumeraciones y herencia según la semántica que exponga la API.

La interfaz será un CRUD adaptable: no intentes inventar pantallas de negocio como pagos o descuentos solo a partir de nombres de tablas. Incluye internacionalización inicial en español, tema accesible, manejo uniforme de carga/error/vacío y configuración de API mediante --dart-define con alternativa editable para desarrollo. Genera README del proyecto móvil con comandos Android, conexión al backend del mismo hito y configuración de IP local.

Criterio de aceptación: para modelos de ventas, colegio y salud se genera localmente una aplicación Android que se autentica y permite operar cada entidad y relación contra el backend generado desde la misma revisión.
```

## 17. Agente local, APK y Samsung A56 físico

```text
Objetivo: crear Collab Modeler Local Agent para generar Flutter en la computadora, compilar APK e instalar o ejecutar la aplicación en un Samsung A56 físico.

Implementa un agente local de Windows que se comunique únicamente por loopback con la web, valide el origen de la solicitud y acepte modeler-mobile-spec.json firmado y de un solo uso. Al recibir una orden, permite elegir una carpeta segura de salida, genera el proyecto Flutter desde las plantillas locales, verifica Flutter SDK, Android SDK y ADB, y detecta dispositivos mediante adb devices.

Implementa el flujo “Generar app móvil” en la web: obtiene la especificación de una revisión inmutable, solicita confirmación al agente local y muestra progreso, errores y ubicación final del proyecto. El agente debe permitir ejecutar flutter run en el Samsung A56 conectado y generar flutter build apk --release para distribución. Nunca debe aceptar rutas arbitrarias, ejecutar comandos suministrados por el servidor ni exponer un puerto de red externo.

Configura la URL de API para dispositivo físico. No uses localhost en Android: detecta o solicita la IP privada de la computadora, valida que tenga formato seguro y configura la app con --dart-define=API_BASE_URL=http://IP_DE_LA_PC:8080. Documenta depuración USB, firewall de Windows y la necesidad de que PC y Samsung compartan Wi-Fi.

Criterio de aceptación: desde la web se inicia una generación ligada a una revisión; el agente crea el proyecto fuera del ZIP, detecta el Samsung A56, compila o ejecuta Flutter en el teléfono y la app alcanza el backend de la PC mediante su IP local.
```

## 18. Flutter offline-first y sincronización bidireccional

```text
Objetivo: permitir que la aplicación Flutter generada siga funcionando sin conexión sin perder modificaciones.

Implementa almacenamiento local SQLite para datos consultados y una outbox transaccional para operaciones CRUD pendientes. Cada operación debe contener un identificador idempotente, entidad, registro, versión base, fecha y payload. Los cambios locales deben reflejarse de forma optimista. Al recuperar conexión, sincroniza cambios remotos y reintenta la outbox en orden seguro.

Implementa detección de conflicto cuando servidor y dispositivo modifican el mismo dato. Muestra al usuario una comparación de valor local y remoto para conservar, descartar o editar de nuevo; nunca descartes cambios incompatibles de forma silenciosa. Protege tokens con almacenamiento seguro del dispositivo y conserva la sesión cuando el token pueda refrescarse.

Criterio de aceptación: crear, editar y eliminar registros offline funciona; tras recuperar red no se duplican operaciones, los cambios compatibles convergen y los conflictos quedan visibles para decisión del usuario.
```

## 19. IA local en Flutter: texto, voz y fotografía

```text
Objetivo: proporcionar asistencia móvil offline-first para operar datos generados sin comprometer integridad ni privacidad.

Implementa un intérprete local de comandos de texto que produzca propuestas CRUD estructuradas y reutilice las reglas de validación de formularios. Para voz, integra reconocimiento disponible en el dispositivo y convierte la transcripción mediante el mismo intérprete. Para fotografías, implementa captura, permisos, validación y OCR local para extraer textos, códigos y valores que se presenten como propuesta de búsqueda o formulario.

Toda creación, edición o eliminación generada por IA debe mostrar una vista previa y requerir confirmación. No ejecutes código ni SQL proveniente de IA. Cuando haya conexión, permite enviar opcionalmente imágenes o comandos a un proveedor remoto configurado para análisis visual complejo, pero mantén la propuesta bajo confirmación y no bloquees el uso offline. No guardes imágenes, audios, tokens ni transcripciones sensibles en logs.

Criterio de aceptación: texto y voz permiten proponer operaciones CRUD sin red; OCR propone campos desde una foto sin red; cancelar no modifica datos y las operaciones confirmadas se sincronizan al recuperar conexión.
```

## 20. Generación asíncrona de backend y especificación móvil

```text
Objetivo: hacer que la generación del backend ZIP y de la especificación móvil sea confiable, trazable y escalable.

Modela trabajos de generación con estado QUEUED, RUNNING, SUCCEEDED y FAILED, revisión o versión fuente, solicitante, fechas y error seguro. Cada trabajo debe generar un ZIP único solo con backend, README, Docker Compose, contrato OpenAPI y trazabilidad, además de un modeler-mobile-spec.json firmado y vinculado a la misma revisión. El código Flutter se genera exclusivamente en la computadora mediante el agente local y no se almacena dentro del ZIP. En local puede existir un ejecutor compatible, pero en AWS utiliza SQS y trabajadores separados. Guarda ZIP, especificaciones e imágenes en almacenamiento de objetos con nombres no predecibles, cifrado, expiración y URLs firmadas de corta duración.

Implementa endpoints y frontend para iniciar, consultar progreso, reintentar y descargar. Garantiza idempotencia y evita generar dos veces el mismo trabajo por reintentos de red.

Criterio de aceptación: cerrar el navegador no cancela el trabajo, el backend ZIP y la especificación móvil pueden consultarse posteriormente, ambos pertenecen a la misma revisión y solo miembros autorizados pueden descargarlos o enviarlos al agente local.
```

## 21. Seguridad, observabilidad y calidad integral

```text
Objetivo: preparar Collab Modeler para operar de forma segura.

Realiza una revisión de autorización por objeto en todos los endpoints y canales WebSocket. Incluye el backend de la plataforma y los backends generados: registro, contraseñas con hash robusto, JWT de acceso y refresco con revocación, roles, OAuth opcional, almacenamiento seguro de tokens móviles y autorización de sincronización. Protege también el agente local: solo loopback, verificación de origen, especificaciones firmadas y de un solo uso, validación de rutas y prohibición de comandos remotos arbitrarios. Añade límites de carga y frecuencia, validación de archivos, cabeceras de seguridad, CORS por ambiente, protección CSRF, secretos fuera del repositorio y mensajes de error que no filtren detalles internos.

Agrega logs estructurados con correlation ID, métricas, health/readiness checks y trazas para operaciones críticas y trabajos de generación. No registres tokens, cookies, imágenes ni prompts sensibles. Define auditoría de accesos y acciones destructivas.

Implementa pruebas de seguridad para acceso horizontal, enlaces revocados, roles LECTOR/EDITOR/PENDING, XXE, archivos maliciosos, payloads grandes, suscripciones WebSocket, operaciones duplicadas, refresco y revocación de token, especificaciones móviles alteradas y nombres peligrosos usados por el generador.

Criterio de aceptación: la suite de seguridad pasa y existen alertas o métricas útiles para diagnosticar autenticación, base de datos, Redis, WebSocket y generación.
```

## 22. CI/CD, AWS y ambientes

```text
Objetivo: automatizar validación y despliegue reproducible en prueba y producción.

Configura CI para instalar dependencias con lockfiles, compilar frontend web, backend, agente local y Flutter Android, ejecutar pruebas unitarias e integración, validar migraciones, analizar dependencias, construir imágenes y ejecutar terraform fmt/validate. Publica imágenes inmutables identificadas por commit.

Completa Terraform para S3 y CloudFront del frontend, ECS Fargate y ALB para API y workers, ECR, RDS PostgreSQL, ElastiCache Redis, S3 de artefactos, SQS, Amazon SES, Secrets Manager, ACM, HTTPS y CloudWatch. Separa test y producción mediante estados y variables independientes. Configura el dominio/remitente de SES, backups, cifrado, redes privadas, mínimos privilegios y estrategia de rollback.

No ejecutes cambios destructivos en producción automáticamente. El plan de Terraform debe revisarse antes de aplicar.

Criterio de aceptación: un commit aprobado despliega al ambiente de prueba, ejecuta una prueba de humo y permite promover de forma controlada a producción.
```

## 23. Prueba final, documentación y preparación de entrega

```text
Objetivo: verificar el producto completo contra PLAN.md y dejarlo listo para demostración y mantenimiento.

Crea una matriz de trazabilidad que relacione cada requisito de PLAN.md con implementación y pruebas. Ejecuta una prueba integral: registro invitado con nombre completo, verificación por correo, inicio con correo/contraseña y Google vinculado, solicitud pendiente, aprobación como lector o editor, creación de proyecto, enlace compartido, dos participantes, edición simultánea, conflicto, reconexión, comentarios, versión, restauración, texto, voz, imagen, XMI y generación de backend ZIP más especificación móvil. Compila el backend, levanta PostgreSQL, genera Flutter mediante el agente local, detecta un Samsung A56 físico, instala un APK, opera CRUD conectado, opera CRUD sin red y verifica la sincronización y los conflictos.

Completa documentación de arquitectura, API OpenAPI, modelo de datos, ejecución local, configuración de IA, generación Flutter, sincronización offline, despliegue, backups, recuperación, seguridad y guía de usuario. Elimina datos simulados, botones sin implementar, código muerto y artefactos compilados versionados. Registra limitaciones reales y no presentes como terminadas funciones sin prueba.

Criterio de aceptación: todos los requisitos acordados tienen evidencia verificable, la prueba integral pasa en el ambiente de prueba y una persona nueva puede instalar, usar y mantener el sistema siguiendo la documentación.
```

## Orden de entregas sugerido

## 1 - 5
## - Identidad y acceso: prompts 06 y 07.
## - Colaboración completa: prompts 08 al 11.
- Intercambio e IA web: prompts 12 al 14.
- Generación backend, agente local y aplicación móvil: prompts 15 al 20.
- Producción y cierre: prompts 21 al 23.
