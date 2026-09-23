# Matriz de trazabilidad y estado de entrega

Esta matriz relaciona los compromisos de `docs/product/roadmap.md` con el código y la evidencia que se puede repetir. El estado **Automatizado** significa que hay una prueba en el repositorio; **Manual externa** exige infraestructura, credenciales o dispositivo que no se incluye en Git; **Pendiente de validación** nunca equivale a terminado.

| Requisito de `roadmap.md` | Implementación principal | Evidencia | Estado |
| --- | --- | --- | --- |
| Monorepo React, Spring Boot 21, PostgreSQL y Redis | `Frontend-web`, `Backend-web`, `docker-compose.yml` | `pnpm test:all`; healthcheck Compose | Automatizado parcialmente (infraestructura local requiere Docker) |
| Registro por invitación, nombre completo, verificación y recuperación | `auth/AuthService.java`, `AuthController.java`, `V5__accounts_and_invitations.sql` | `AuthServiceTest`, `ExternalSecurityIntegrationTest` | Automatizado; envío real de correo es manual con Mailpit/SES |
| Registro público bloqueado | `AuthService.register` y `AuthApp.tsx` | `AuthServiceTest.directRegistrationIsRejectedWithoutInvitation` | Automatizado |
| Login local y sesiones/CSRF | `SecurityConfig.java`, `AuthController.java`, `Frontend-web/src/shared/api/api.ts` | `ExternalSecurityIntegrationTest`, `api.test.ts` | Automatizado |
| Roles OWNER/EDITOR/READER/PENDING, enlaces y revocación | `access/*`, `V2__members.sql`, `V5__accounts_and_invitations.sql` | `AccessServiceTest`, `AccessControllerTest` | Automatizado |
| Editor UML, operaciones, deshacer y persistencia | `DiagramService.java`, `operationReducer.ts`, React Flow | `DiagramServiceTest`, `DiagramOperationValidationTest`, `operationReducer.test.ts` | Automatizado |
| Edición simultánea, conflicto, outbox y reconexión | `DiagramService.java`, `store.ts`, `collaboration.md` | `PostgresPersistenceTest`, `store.test.ts` | Automatizado por unidad/integración; dos navegadores es manual |
| Presencia y WebSocket con Redis | `collaboration/*`, `WebSocketConfig.java` | `DiagramChannelInterceptorTest`, `CollaborationEventPublisherTest`, `WebSocketConfigTest` | Automatizado de autorización/publicación; multi-réplica es manual |
| Comentarios, actividad, hitos y restauración no destructiva | `comment/*`, `activity/*`, `version/*`, migraciones V3/V6/V7 | `CommentServiceTest`, `DiagramVersionServiceTest` | Automatizado |
| Texto, voz, imagen y confirmación de operaciones IA | `ai/*`, `assistant.ts`, `speech.ts`, `imageImport.ts` | pruebas `ai/*`, `assistant.test.ts`, `speech.test.ts`, `imageImport.test.ts` | Automatizado sin proveedor; llamada IA real es manual externa |
| Importación/exportación XMI 2.1 segura | `exchange/*`, `uml-json-contract.md` | `XmiServiceTest`, `ExchangeControllerTest`, fixtures EA | Automatizado |
| ZIP Spring Boot desde revisión inmutable y trazabilidad | `generation/BackendGenerator.java`, `ModelValidator.java` | `BackendGeneratorTest`, `GeneratedBackendPostgresIntegrationTest` | Automatizado; compilación del ZIP se ejecuta en integración cuando Docker está disponible |
| Especificación móvil firmada y agente local | `MobileSpecService.java`, `mobile-flutter/tools/local-agent/*` | `FlutterGeneratorTest`, `agent:test` | Automatizado |
| Flutter CRUD, autenticación, SQLite/outbox y conflictos | `mobile-flutter/lib`, `core/sync/*` | `flutter analyze`, `flutter test` | Automatizado cuando Flutter está instalado; dispositivo físico es manual externa |
| Terraform/AWS, secretos, backups y despliegue | `infra/terraform`, `.github/workflows` | CI: `terraform fmt -check`, `terraform validate` | Automatizado para sintaxis; aplicar AWS y recuperación son manuales controlados |

## Criterio de liberación

No se debe marcar una entrega como aprobada hasta que `pnpm test:all` y las secciones aplicables de [release-validation.md](./release-validation.md) estén en verde. Los pasos marcados como manual externa deben adjuntar sus evidencias (logs sanitizados, captura o URL de la ejecución) a la entrega.
