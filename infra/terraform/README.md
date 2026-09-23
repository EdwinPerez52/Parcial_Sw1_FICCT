# AWS: infraestructura y despliegue controlado

Este directorio no aplica infraestructura automáticamente desde una computadora. Los flujos de GitHub Actions aplican el ambiente `test` al integrar en `main`; producción solo se inicia manualmente con el SHA completo que ya superó prueba y con la aprobación obligatoria del environment `production` de GitHub.

## Recursos

Cada ambiente tiene estado, VPC y CIDR propios. Las tareas ECS Fargate de API y worker viven en subredes privadas, detrás de ALB, con rollback automático si falla su despliegue. RDS PostgreSQL usa contraseña administrada por RDS/Secrets Manager, cifrado, copias y protección de borrado en producción. Redis, S3 privado para artefactos con KMS, SQS con DLQ, CloudWatch, SES, ACM, CloudFront y el bucket privado del frontend se configuran desde el mismo plan. Las tareas tienen un rol de aplicación mínimo separado del rol de ejecución de ECS.

`bootstrap/` crea una sola vez y bajo revisión el bucket versionado de estado, tabla de bloqueo y repositorio ECR inmutable. El stack principal lo consulta como data source; así no se depende de una imagen que aún no existe al crear ECR.

## Primera instalación manual

1. Crea la cuenta AWS, activa MFA y crea un usuario/rol temporal de administrador solo para el bootstrap.
2. En `infra/terraform/bootstrap`, ejecuta `terraform init`, `terraform plan` y, tras revisar el plan, `terraform apply`. Guarda los outputs `state_bucket`, `lock_table` y `ecr_repository`.
3. Crea en Route 53 (o delega) una zona hospedada por ambiente. Configura `domain_name` y `route53_zone_id`; el stack publica los registros de ACM, CloudFront, ALB y SES/DKIM. Usa dominios distintos, por ejemplo `test.tudominio.com` y `app.tudominio.com`.
4. Solicita el paso de SES de sandbox a producción y crea las credenciales SMTP. Nunca se guardan en `.tfvars` ni Git.
5. En GitHub, crea environments `test` y `production`; exige revisores para `production`. En cada environment configura las variables `AWS_REGION`, `TF_STATE_BUCKET`, `TF_LOCK_TABLE`, `DOMAIN_NAME`, `ROUTE53_ZONE_ID` y `ECR_REGISTRY`, y los secretos indicados abajo. Configura autenticación OIDC para un rol con permisos limitados a estos recursos y coloca su ARN en `AWS_DEPLOY_ROLE_ARN`.

Secrets requeridos por ambiente: `AWS_DEPLOY_ROLE_ARN`, `GENERATION_ENCRYPTION_KEY`, `GENERATION_DOWNLOAD_SECRET`, `MOBILE_SPEC_SECRET`, `SES_SMTP_USERNAME`, `SES_SMTP_PASSWORD` y, si se usa IA, `AI_API_KEY`. Terraform marca esos valores sensibles; cifra y restringe el bucket de estado.

## Uso local de Terraform

No uses `apply` sin inspeccionar el plan. Copia `environments/backend.hcl.example` fuera de Git, ajusta `key` a `collab-modeler/test/terraform.tfstate` o `collab-modeler/prod/terraform.tfstate`, y ejecuta:

```powershell
terraform init -backend-config=backend.hcl
terraform fmt -check -recursive
terraform validate
terraform plan -var-file=environments/test.tfvars -out=test.tfplan
terraform show test.tfplan
```

Los secretos deben entrar mediante `TF_VAR_*` del gestor de secretos o la sesión, no mediante archivos. No hay un flujo que destruya producción automáticamente; `deletion_protection=true`, respaldos de 14 días, Multi-AZ y dos réplicas de Redis permanecen activos allí.
