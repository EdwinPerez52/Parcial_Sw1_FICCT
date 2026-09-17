# Colaboración y convergencia

## Canal en tiempo real

El navegador abre `/ws` con la cookie de sesión y se suscribe a `/topic/diagrams/{id}`. El servidor autoriza cada suscripción y cada mensaje de presencia contra `diagram_members`. Los eventos de operación, restauración, comentario, actividad y presencia comparten un sobre `{ type, payload }`.

Cada instancia entrega el evento a su broker local y publica el mismo sobre en `collab:model-events`. Las otras instancias lo reciben por Redis y lo entregan a sus clientes locales. Un identificador de instancia evita ecos.

La presencia se almacena por diagrama y sesión con TTL. El heartbeat renueva la entrada; al desconectar se elimina inmediatamente y, ante una desconexión abrupta, expira sin crear usuarios fantasma. Cursores y selecciones nunca se persisten en PostgreSQL.

## Outbox y reconexión

Cada acción local crea una operación con UUID y pasa por `pending`, `acknowledged` o `rejected`. Solo las operaciones no confirmadas se guardan en `localStorage`; no se guardan secretos ni datos de sesión.

1. La interfaz aplica la operación de forma optimista.
2. Al reconectar solicita las operaciones posteriores a la revisión confirmada.
3. Aplica esos cambios en orden de `resultRevision`.
4. Reproduce encima las operaciones locales pendientes.
5. Reenvía la outbox en orden y conserva el `operationId` en cada reintento.

Una respuesta o evento remoto nunca sustituye a ciegas el estado optimista: primero actualiza la base confirmada y luego reproduce la outbox. Un `409` mueve la operación a `rejected` y conserva tanto el estado local como el del servidor para la interfaz de conflicto.

## Historial y comentarios

Deshacer y rehacer calculan un lote de operaciones inversas, no una sustitución completa del documento. Los hitos persistentes son independientes del historial local. Restaurar exige que no haya cambiado la revisión desde la previsualización y registra una nueva operación `MODEL_RESTORED`.

Los comentarios pueden apuntar al diagrama, clase, atributo, asociación, enumeración o generalización. Las respuestas usan `parent_comment_id`. Lectores, editores y propietarios pueden comentar; solo editores y propietarios pueden resolver o reabrir conversaciones.
