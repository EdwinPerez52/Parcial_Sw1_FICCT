# Validación final y guía de operación

## Preparación local

1. Instala Node 22, JDK 21, Docker Desktop y, para Android, Flutter y ADB.
2. Ejecuta `pnpm setup`, copia `.env.example` a `.env` y configura solo los secretos locales necesarios.
3. Arranca `pnpm dev`. Espera `http://localhost:8080/actuator/health` con estado `UP` y abre Mailpit en `http://localhost:8025`.
4. Ejecuta la puerta automatizada `pnpm test:all`. Si Docker no está disponible, Maven omite explícitamente las pruebas Testcontainers: esa omisión debe registrarse, no interpretarse como éxito de integración.

## Recorrido integral de demostración

Ejecuta cada caso con dos perfiles de navegador independientes y conserva la evidencia indicada.

| Paso | Acción y resultado esperado | Evidencia |
| --- | --- | --- |
| 1 | Configura `APP_BOOTSTRAP_ADMIN_EMAIL`, abre su invitación de Mailpit, registra nombre completo y verifica el correo. | URL de Mailpit y pantalla `Cuenta verificada` |
| 2 | Intenta `POST /api/v1/auth/register` sin token: debe devolver `403 INVITATION_REQUIRED`. Inicia sesión local. | respuesta HTTP sanitizada |
| 3 | Solicita recuperación desde la pantalla de inicio, abre el enlace de Mailpit y establece una contraseña nueva. Inicia sesión con esa contraseña. | correo de Mailpit y pantalla de proyectos |
| 4 | Crea un diagrama, rota el enlace compartido, registra el segundo usuario desde él y verifica su correo. Debe quedar `PENDING`; el propietario lo convierte a READER y luego EDITOR. | panel de miembros y respuestas 403/200 |
| 5 | Con ambos participantes, crea clases, atributos, asociación y herencia. Edita propiedades distintas en paralelo (convergencia) y la misma propiedad (409/comparador). Desconecta y reconecta un navegador para comprobar recuperación de outbox. | revisión, panel de conflicto y DevTools sin credenciales |
| 6 | Crea comentario y respuesta, guarda hito, altera el modelo y restaura el hito. La restauración debe incrementar revisión y conservar historial. | lista de versiones y actividad |
| 7 | Ejecuta texto y voz; analiza PNG/JPEG/WebP y cancela una previsualización. Confirma otra propuesta. Sin `AI_API_KEY`, los comandos locales siguen funcionando y el proveedor responde 503 para comandos complejos. | propuesta, advertencias y revisión final |
| 8 | Importa los tres fixtures XMI de `Backend-web/src/test/resources/xmi`, revisa advertencias y exporta XMI. | XMI exportado y conteos del modelo |
| 9 | Genera un backend y `modeler-mobile-spec.json` desde un hito. Descomprime el ZIP fuera del repositorio, ejecuta `./mvnw verify` y `docker compose up` dentro de él. | salida Maven, healthcheck y `model-traceability.json` |
| 10 | Ejecuta `pnpm agent:install` y `pnpm agent:start`; solicita la spec de agente desde un job terminado. Genera Flutter, conecta ADB con `adb reverse tcp:8080 tcp:8080` o usa la IP privada y ejecuta la app. | SSE del agente, `adb devices -l`, pantalla CRUD |
| 11 | En Flutter crea, actualiza y borra registros conectado. Desactiva red, repite CRUD, reactiva y verifica cola idempotente; fuerza dos ediciones incompatibles y resuélvelas desde la pantalla de conflictos. | SQLite/outbox sanitizada, registro remoto y UI de conflicto |

## Operación, recuperación y seguridad

- **Backups:** RDS conserva copias automatizadas según `backup_retention_days`; antes de una restauración, bloquea escrituras, toma snapshot, restaura a una instancia nueva y valida `flyway:validate`, healthcheck y un recorrido de lectura. No apuntes producción a una base restaurada sin aprobación explícita.
- **Recuperación de artefactos:** los trabajos durables se reintentan por `POST /api/v1/diagrams/{id}/generation-jobs/{jobId}/retry`. Los enlaces caducan; nunca se recuperan desde logs ni se exponen claves HMAC.
- **Despliegue:** consulta `infra/terraform/README.md`. CI valida y crea el plan de `test`; producción solo usa ejecución manual con SHA completo aprobado. Revisa el plan antes de `apply`.
- **Secretos:** usa Secrets Manager en AWS y `.env` solo local. Rota `MOBILE_SPEC_SECRET`, claves de cifrado y descarga de generación como una operación coordinada: los artefactos previos pueden dejar de ser verificables.
- **Incidente:** preserva logs sanitizados, revoca enlaces compartidos y sesiones afectadas, rota credenciales, evalúa integridad Flyway y documenta el alcance antes de reabrir escrituras.

## Limitaciones verificables

- La transcripción, visión y respuestas de IA externas requieren proveedor configurado. Las pruebas no envían datos a proveedores reales.
- La instalación en Samsung A56, ADB y conectividad Wi-Fi/USB no se pueden validar sin el dispositivo físico y su SDK.
- XMI conserva semántica soportada, no estilos ni extensiones propietarias de Enterprise Architect.
