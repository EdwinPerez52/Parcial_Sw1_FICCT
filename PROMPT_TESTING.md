# Prompt de testing y auditoría integral — Collab Modeler (incrementos 01–11)

```text
Actúa como ingeniero senior de calidad, seguridad y arquitectura de software. Trabaja sobre el monorepo existente de Collab Modeler y realiza una auditoría técnica y funcional exhaustiva de lo implementado para los prompts de desarrollo 01 al 11 de PROMPTS_DESARROLLO.md.

Tu objetivo en esta primera fase es encontrar, reproducir, documentar y priorizar defectos. No debes corregir código, migraciones, configuración, dependencias, documentación funcional ni infraestructura. La única modificación permitida durante la auditoría es crear o actualizar, en la raíz del repositorio, el archivo Testing.md con el reporte solicitado. Puedes crear datos efímeros dentro de Testcontainers, contenedores Docker, PostgreSQL, Redis y Mailpit exclusivamente para ejecutar las pruebas.

Cuando termines Testing.md, detén la ejecución y espera la aprobación explícita del usuario antes de aplicar cualquier arreglo.

## 1. Reglas obligatorias

1. Antes de probar, lee completamente:
   - PROMPTS_DESARROLLO.md, especialmente los prompts 01–11.
   - PLAN.md.
   - README.md.
   - docs/architecture.md.
   - docs/collaboration.md.
   - docs/uml-json-contract.md.
   - package.json, pnpm-workspace.yaml y los scripts de la carpeta scripts.
   - docker-compose.yml y los Dockerfiles.
   - backend_parcial/pom.xml y los perfiles application*.yml.
   - Las migraciones Flyway existentes.
   - El código y las pruebas relacionados del backend y frontend.
2. No asumas que una funcionalidad existe porque esté documentada o porque haya una prueba con nombre parecido. Contrasta especificación, implementación, prueba automatizada y comportamiento observable.
3. No declares una prueba aprobada si no fue ejecutada o inspeccionada con evidencia suficiente.
4. No ocultes pruebas omitidas. Registra cada omisión y su causa.
5. No muestres ni copies secretos en consola o Testing.md. Solo indica si una variable requerida está configurada o ausente.
6. No uses servicios, datos ni credenciales de producción.
7. No modifiques migraciones Flyway existentes ni ejecutes operaciones destructivas sobre bases de datos ajenas al entorno local de pruebas.
8. No audites como requisito los prompts 12 en adelante. Si código posterior afecta directamente una función de los prompts 01–11, puedes señalar la interacción, pero no reportes como defecto la ausencia de requisitos posteriores.
9. Si el repositorio contiene cambios previos del usuario, presérvalos y no los atribuyas a esta auditoría.
10. Si una herramienta requerida no está disponible, intenta una alternativa segura. Si no existe alternativa, registra la prueba como BLOQUEADA POR ENTORNO con evidencia; no la marques como aprobada o fallida.

## 2. Preparación y línea base

Registra en Testing.md, sin exponer datos sensibles:

- Fecha y hora de la auditoría.
- Sistema operativo y arquitectura.
- Versiones de Node.js, Corepack/pnpm, Java, Maven Wrapper, Docker/Compose, PostgreSQL, Redis, Flutter y ADB cuando apliquen.
- Estado de disponibilidad de Docker.
- Estado del árbol de trabajo o, si no existe metadato Git utilizable, la indicación correspondiente.
- Variables necesarias presentes o ausentes, informando solo el nombre y el estado, nunca su valor.
- Comandos que se ejecutarán y alcance de cada uno.

Comprueba que los comandos documentados sean reproducibles y coherentes con los archivos reales del proyecto. Ejecuta, cuando el entorno lo permita, las pruebas y compilaciones raíz, web y backend. Como mínimo revisa y ejecuta los equivalentes reales de:

- pnpm test:all
- pnpm test
- pnpm build
- backend_parcial/mvnw.cmd verify en Windows o ./mvnw verify en sistemas compatibles
- docker compose config
- Levantamiento local de PostgreSQL, Redis, Mailpit, API y web
- Health checks y disponibilidad de los puertos documentados
- flutter doctor y comprobación de ADB solo como validación del entorno reproducible del prompt 01; no exijas una aplicación Flutter, pues corresponde a incrementos posteriores

No te limites a ejecutar la suite general. Revisa sus scripts para detectar pasos omitidos, falsos positivos, pruebas saltadas, códigos de salida ignorados y dependencias implícitas.

## 3. Matriz obligatoria de auditoría por incremento

Crea una matriz de trazabilidad para todos los criterios de aceptación de los prompts 01–11. Para cada requisito indica: ID del prompt, requisito verificable, componente implicado, método o prueba utilizada, resultado APROBADO/FALLIDO/BLOQUEADO/NO IMPLEMENTADO y referencia a evidencia o hallazgo.

### Prompt 01 — Entorno reproducible y línea base

Verifica wrappers y versiones fijadas; instalación desde una copia limpia en la medida posible; scripts raíz; builds; pruebas; perfiles; .gitignore; Dockerfiles; Docker Compose; health checks; PostgreSQL; Redis; Mailpit; detención segura; documentación de variables; Flutter SDK; Android SDK; ADB y Samsung A56. Detecta artefactos generados o secretos que puedan quedar versionados.

### Prompt 02 — PostgreSQL, Flyway y persistencia

Compara todas las entidades JPA con V1 y migraciones posteriores. Revisa claves primarias y foráneas, índices, unicidad, nulabilidad, tipos, cascadas y borrado referencial. Comprueba que Hibernate valide el esquema, que una base vacía migre y que no se hayan alterado migraciones ya ejecutables. Ejecuta pruebas con PostgreSQL real/Testcontainers y persistencia tras reinicio. Verifica que tokens de invitación, sesión de flujo, verificación y recuperación nunca se persistan en claro. No aceptes H2 como única evidencia de compatibilidad PostgreSQL.

### Prompt 03 — Modelo UML semántico

Comprueba paridad semántica entre Java, TypeScript, JSON y persistencia para clases, atributos, enumeraciones y valores, asociaciones, cardinalidades, roles, lado propietario, generalizaciones, posiciones, UUID y versiones. Prueba serialización y deserialización, nulabilidad, unicidad, clave primaria y tipos escalares. Realiza round-trip de modelos representativos de ventas, colegio y salud, comprobando igualdad semántica y ausencia de pérdida de información.

### Prompt 04 — Operaciones, validación e idempotencia

Prueba individualmente todas las operaciones declaradas: crear, renombrar, mover y eliminar clases; crear, editar, reordenar y eliminar atributos; crear, editar y eliminar asociaciones; crear, editar y eliminar enumeraciones y valores; crear y eliminar generalizaciones; y BATCH atómico. Verifica operationId, baseRevision y expectedElementVersion, incluido CLASS_MOVED. Cubre referencias inexistentes, nombres inválidos o duplicados, cardinalidades inválidas, ciclos de herencia, rollback completo del BATCH, reintentos idempotentes, concurrencia sobre elementos distintos y conflictos 409 sobre la misma propiedad. Valida la estructura completa de los errores, incluida revisión, elemento y versiones.

### Prompt 05 — Editor visual UML

Prueba el panel de propiedades y el lienzo para crear, editar y eliminar todos los elementos soportados; tipos, PK, requerido, único, cardinalidades, roles y lado propietario; selección y movimiento múltiple; confirmación de eliminación; atajos; validación cercana al elemento; asociaciones, cardinalidades y herencia visibles. Confirma que cada acción manual emita una DiagramOperation válida, que Zustand no mutile ni mute directamente el estado y que los modelos de ventas, colegio y salud puedan construirse, guardarse, recargarse y conservarse. Revisa accesibilidad básica de formularios, mensajes, foco, teclado y contraste cuando sea comprobable.

### Prompt 06 — Identidad, autenticación e invitaciones

Audita el modelo de cuentas e identidades, normalización de correo, vinculación entre Google y contraseña, migración de miembros y sujeto estable. Prueba registro únicamente con invitación válida, campos requeridos, confirmación de contraseña, verificación de correo, recuperación y restablecimiento, expiración, uso único, rotación y revocación. Verifica Argon2id, hash de tokens, no enumeración de correos, rate limiting, cookies HttpOnly/Secure/SameSite según perfil y CSRF. Comprueba bootstrap de APP_BOOTSTRAP_ADMIN_EMAIL y rol de administrador. Cubre /api/v1/auth/me, login, registro, verificación, recuperación, restablecimiento, logout y /join/{token}; respuestas 401, 403, 404 y 409; y prohibición total de leer, editar, exportar o generar para no miembros.

Para Google OAuth, realiza la validación externa si existen credenciales locales válidas y un redirect URI configurado para pruebas. Comprueba correo verificado, vinculación con cuenta local y rechazo del correo no verificado cuando pueda reproducirse de forma segura. Si no hay credenciales o la interacción con Google requiere intervención humana no disponible, ejecuta todas las pruebas locales, contractuales y de configuración posibles y marca exclusivamente el recorrido externo como BLOQUEADO POR ENTORNO. Aplica el mismo criterio a Amazon SES: usa Mailpit para el flujo local y valida SES externo solo si existe un entorno no productivo autorizado.

### Prompt 07 — Inicio de sesión, registro y proyectos

Prueba AuthGate y /auth/me antes de cargar el editor; interfaz en español; Google y correo/contraseña; estados accesibles de carga y error; ausencia de credenciales o tokens de sesión en localStorage/sessionStorage; cierre de sesión; conservación del destino de invitación; registro visible únicamente con invitación; verificación y recuperación; redirección al diagrama o listado de proyectos; creación, apertura, rotación y revocación de enlaces; identidad real visible; y manejo seguro y accionable de 401, 403, 404, 409 y errores de red. Verifica especialmente que usuarios anónimos, no verificados, pendientes, lectores, editores y propietarios vean y puedan hacer solo lo autorizado.

### Prompt 08 — Colaboración, presencia y WebSocket

Comprueba autenticación del handshake y autorización STOMP en SUBSCRIBE y SEND. Intenta acceder a tópicos de diagramas ajenos con usuario anónimo, no miembro y miembro de otro diagrama. Revisa orígenes permitidos y rechazo de comodines en producción. Prueba con dos sesiones la propagación de operaciones, identidad, ingreso/salida, cursores, selección y actividad. Verifica heartbeat, TTL, eliminación de usuarios fantasma y que los movimientos efímeros no lleguen a PostgreSQL. Cuando sea posible, levanta dos instancias del backend y confirma fan-out y convergencia mediante Redis, sin ecos ni duplicados.

### Prompt 09 — Reconexión, cola offline y conflictos

Prueba cola persistente y estados pending/acknowledged/rejected; conservación de operationId; actualización optimista; caída de red; edición offline; recarga del navegador; reconexión; recuperación de operaciones posteriores a la revisión; orden alterado de respuestas y eventos; reenvío idempotente; y replay de operaciones locales sobre la base confirmada. Verifica que una instantánea remota no reemplace silenciosamente cambios optimistas. Fuerza conflictos reales y valida la comparación local/servidor y las acciones descartar, reintentar y reaplicar. Comprueba que errores de almacenamiento local no destruyan el estado de la aplicación.

### Prompt 10 — Undo, redo, hitos y restauración

Prueba secuencias de varias operaciones, undo y redo mediante operaciones compensatorias, invalidación correcta de redo tras una nueva acción y comportamiento con operaciones pendientes o rechazadas. Comprueba creación, listado, previsualización y restauración de hitos con nombre, autor, fecha y revisión. La restauración debe crear una revisión nueva, conservar operaciones y versiones anteriores, rechazar revisiones esperadas obsoletas, difundirse por WebSocket y converger en todos los clientes.

### Prompt 11 — Comentarios y actividad

Prueba comentarios del diagrama y comentarios anclados a clase, atributo, asociación, enumeración y generalización; respuestas/hilos; autor, fecha, estado y contexto; resolución y reapertura; persistencia tras reinicio; actividad duradera separada de presencia; y difusión en tiempo real. Comprueba permisos de lectura, creación, respuesta y resolución para anónimo, no miembro, pendiente, lector, editor y propietario. Prueba referencias a elementos inexistentes o eliminados y concurrencia sobre conversaciones.

## 4. Técnicas de auditoría obligatorias

Combina, según corresponda:

- Inspección estática de código, configuración, migraciones y contratos.
- Pruebas unitarias y de componentes existentes.
- Pruebas de integración del backend.
- Pruebas reales con PostgreSQL, Redis y Mailpit.
- Pruebas HTTP de endpoints, autenticación, autorización, CSRF y errores.
- Pruebas de UI en navegador para los recorridos principales y estados visuales.
- Pruebas WebSocket/STOMP con dos sesiones y usuarios con roles distintos.
- Pruebas de concurrencia, idempotencia, reconexión y orden de eventos.
- Pruebas negativas y entradas límite o maliciosas relevantes.
- Revisión de logs para detectar excepciones, secretos, PII, tokens o contraseñas.
- Comparación entre documentación, contrato, código y comportamiento real.

No añadas dependencias ni suites permanentes durante esta fase. Puedes usar comandos ad hoc y archivos temporales fuera del repositorio o dentro del directorio temporal del sistema, eliminándolos al terminar. Conserva en Testing.md los comandos reproducibles y la evidencia mínima necesaria.

## 5. Clasificación de severidad

Clasifica cada defecto por su impacto más alto, evitando duplicados:

- CRÍTICO: pérdida o corrupción de datos; acceso no autorizado significativo; exposición de secretos o credenciales; bypass de autenticación/autorización; ejecución remota; imposibilidad total de instalar, migrar o usar el sistema; incumplimiento que compromete todo el producto.
- ALTO: función principal de un prompt 01–11 ausente o rota; pérdida silenciosa de cambios; conflicto o idempotencia incorrectos; permisos de rol vulnerados; flujo de autenticación, persistencia, colaboración o restauración inutilizable; fallo reproducible sin alternativa razonable.
- MEDIO: comportamiento incorrecto con alcance limitado; validación incompleta; error recuperable; inconsistencia entre capas; caso borde relevante; problema de accesibilidad o compatibilidad que afecta una parte del flujo.
- MEJORA MENOR: problema cosmético, de mensajes, mantenibilidad, documentación, observabilidad o experiencia que no compromete la operación principal.

Una prueba bloqueada por falta de credenciales, dispositivo o servicio externo no es automáticamente un bug. Solo crea un hallazgo si existe evidencia en código, configuración o comportamiento local de una implementación defectuosa.

## 6. Estructura obligatoria de Testing.md

Crea Testing.md con esta estructura:

# Reporte de testing y auditoría — Prompts 01–11

## 1. Resumen ejecutivo
- Veredicto general.
- Cantidad de hallazgos por severidad.
- Riesgos principales.
- Alcance efectivamente verificado.

## 2. Entorno y línea base
- Versiones y servicios.
- Estado de configuración sin valores secretos.
- Comandos ejecutados y códigos de salida.

## 3. Matriz de trazabilidad 01–11
- Una fila por requisito verificable con estado y evidencia.

## 4. Hallazgos críticos
## 5. Hallazgos altos
## 6. Hallazgos medios
## 7. Mejoras menores

Cada hallazgo debe usar esta plantilla:

### [SEVERIDAD-ID] Título breve
- Estado: ABIERTO
- Prompts afectados:
- Componentes/rutas afectadas:
- Impacto:
- Evidencia:
- Pasos exactos para reproducir:
- Resultado esperado:
- Resultado actual:
- Causa probable, diferenciada de los hechos confirmados:
- Pruebas que deberán ejecutarse después del arreglo:
- Criterio verificable de cierre:

## 8. Pruebas aprobadas
## 9. Pruebas fallidas
## 10. Pruebas bloqueadas o no ejecutadas
- Motivo, impacto sobre la confianza y acción necesaria para desbloquear cada una.

## 11. Cobertura ausente y riesgos residuales
## 12. Conclusión y siguiente paso

Mantén separados los defectos confirmados, las sospechas que requieren más evidencia y las limitaciones del entorno. Incluye fragmentos breves de salida o referencias a reportes, pero no vuelques logs enormes. Usa rutas y líneas cuando ayuden a localizar el problema.

## 7. Condición de parada obligatoria

Al concluir la auditoría:

1. Revisa que Testing.md sea autosuficiente, reproducible y no contenga secretos.
2. Confirma que cada prompt 01–11 aparezca en la matriz, incluso si quedó bloqueado.
3. Confirma que no modificaste archivos distintos de Testing.md.
4. Finaliza con el texto: "Auditoría finalizada; no se aplicaron correcciones. Esperando aprobación explícita para iniciar la remediación."
5. Detén la ejecución. No corrijas ningún hallazgo, aunque sea crítico, hasta recibir aprobación explícita del usuario.

## 8. Segunda fase — ejecutar únicamente después de la aprobación

Cuando el usuario autorice expresamente los arreglos, conserva Testing.md como registro vivo y trabaja en este orden: CRÍTICOS, ALTOS, MEDIOS y MEJORAS MENORES.

Para cada grupo de severidad:

1. Revalida primero que cada hallazgo siga siendo reproducible.
2. Implementa únicamente las correcciones pertenecientes a ese grupo, preservando funcionalidades existentes y sin reescribir migraciones Flyway ya aplicables.
3. Añade o ajusta las pruebas automatizadas mínimas que demuestren cada regresión corregida.
4. Ejecuta únicamente las pruebas directamente afectadas por las correcciones del grupo. Incluye compilación parcial cuando sea necesaria para que el resultado sea fiable. No ejecutes todavía la regresión general.
5. Si una prueba afectada falla, corrige y repite solo ese conjunto hasta obtener un resultado estable o documentar un bloqueo real.
6. Actualiza cada hallazgo en Testing.md con archivos cambiados, decisión aplicada, comandos ejecutados, resultados y estado CORREGIDO/VERIFICADO, PARCIAL o BLOQUEADO.
7. Añade un resumen de cierre del grupo antes de avanzar a la siguiente severidad.

Después de terminar todos los grupos autorizados, ejecuta una única regresión general mediante pnpm test:all y, además, las verificaciones integrales locales que el script no cubra: Docker Compose y health checks, flujos HTTP/UI, PostgreSQL, Redis, Mailpit, WebSocket con dos sesiones y servicios externos configurados. Actualiza Testing.md con el resultado final, los defectos restantes y los riesgos residuales. No declares el software aprobado si quedan fallos críticos o altos abiertos, pruebas generales fallidas o recorridos esenciales sin verificar.
```
