# Despliegue AWS

1. Construir y publicar la imagen de `backend_parcial` en ECR.
2. Copiar `terraform.tfvars.example` a un archivo no versionado y completar secretos e imagen inmutable.
3. Ejecutar `terraform init`, `terraform plan` y `terraform apply`.
4. Compilar `frontend_parcial` y sincronizar `frontend_parcial/dist` con el bucket mostrado en `web_bucket`.
5. Configurar en Google OAuth la URL `/login/oauth2/code/google` bajo `application_url`.

El estado de Terraform debe almacenarse en un backend S3 con bloqueo para trabajo en equipo; su bucket se administra separadamente para evitar que este stack pueda destruir su propio estado.
