# Comandos útiles de Collab Modeler

Ejecuta estos comandos desde la carpeta raíz del proyecto, donde están `package.json` y `docker-compose.yml`.

## Direcciones locales

- Web: <http://localhost:5173>
- API: <http://localhost:8080>
- Salud de la API: <http://localhost:8080/actuator/health>
- Mailpit: <http://localhost:8025>
- PostgreSQL: `localhost:5432`
- Redis: `localhost:6379`

## Preparar el proyecto por primera vez

```powershell
corepack pnpm setup
```

## Levantar la aplicación completa con Docker

Primera ejecución o reconstrucción de todo:

```powershell
docker compose up -d --build
```

Levantar contenedores ya construidos:

```powershell
docker compose up -d
```

Ver el estado de todos los servicios:

```powershell
docker compose ps
```

## Reconstruir después de modificar código

Solo frontend:

```powershell
docker compose up -d --build web
```

Solo backend:

```powershell
docker compose up -d --build api
```

Frontend y backend:

```powershell
docker compose up -d --build api web
```

Si el navegador sigue mostrando una versión anterior, pulsa `Ctrl+F5`.

## Desarrollo rápido sin reconstruir imágenes

Abre tres terminales distintas.

Terminal 1 — PostgreSQL y Redis:

```powershell
corepack pnpm dev:infra
```

Si vas a probar registro, recuperación de contraseña o correos, levanta también Mailpit:

```powershell
docker compose up -d mailpit
```

Terminal 2 — backend Spring Boot:

```powershell
corepack pnpm dev:api
```

Después de modificar Java, detén este proceso con `Ctrl+C` y vuelve a ejecutar el comando.

Terminal 3 — frontend Vite con actualización automática:

```powershell
corepack pnpm dev:web
```

Los cambios de React, TypeScript y CSS aparecen automáticamente en el navegador.

## Levantar servicios Docker por separado

```powershell
docker compose up -d postgres
docker compose up -d redis
docker compose up -d mailpit
docker compose up -d api
docker compose up -d web
```

Al levantar `api` o `web`, Compose también inicia las dependencias necesarias.

## Logs y diagnóstico

Todos los logs:

```powershell
docker compose logs -f
```

Logs por servicio:

```powershell
docker compose logs -f api
docker compose logs -f web
docker compose logs -f postgres
docker compose logs -f redis
```

Mostrar únicamente las últimas 100 líneas del backend:

```powershell
docker compose logs --tail 100 api
```

Salir del seguimiento de logs: `Ctrl+C`. Esto no detiene los contenedores.

Comprobar la salud de la API:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Validar la configuración de Compose:

```powershell
docker compose config
```

## Reiniciar y detener

Reiniciar servicios sin reconstruir imágenes:

```powershell
docker compose restart api
docker compose restart web
docker compose restart postgres
```

Esto no incorpora cambios recientes del código. Para eso usa `up -d --build`.

Detener un único servicio:

```powershell
docker compose stop api
```

Detener todo conservando los datos de PostgreSQL:

```powershell
docker compose down
```

También puedes usar:

```powershell
corepack pnpm stop
```

## Base de datos PostgreSQL

Abrir la consola SQL dentro del contenedor:

```powershell
docker compose exec postgres psql -U modeler -d modeler
```

Comandos útiles dentro de `psql`:

```text
\dt                 listar tablas
\d nombre_tabla     describir una tabla
SELECT current_database();
\q                  salir
```

Comprobar que PostgreSQL responde:

```powershell
docker compose exec postgres pg_isready -U modeler
```

## Pruebas y compilación

Pruebas del frontend:

```powershell
corepack pnpm test
```

Compilar el frontend:

```powershell
corepack pnpm build
```

Pruebas del backend:

```powershell
Set-Location backend_parcial
.\mvnw.cmd test
Set-Location ..
```

Verificación completa del backend:

```powershell
Set-Location backend_parcial
.\mvnw.cmd verify
Set-Location ..
```

Todas las verificaciones disponibles del monorepo:

```powershell
corepack pnpm test:all
```

## Reconstrucción sin caché

Backend:

```powershell
docker compose build --no-cache api
docker compose up -d api
```

Frontend:

```powershell
docker compose build --no-cache web
docker compose up -d web
```

Ver el espacio utilizado por Docker:

```powershell
docker system df
```

## Borrar completamente la base de datos

> **Cuidado:** elimina el volumen de PostgreSQL y todos los usuarios, diagramas, versiones y demás datos guardados.

```powershell
docker compose down -v
```

Después puedes crear una base limpia:

```powershell
docker compose up -d --build
```

## Resumen cotidiano

Después de cambios en backend y frontend:

```powershell
docker compose up -d --build api web
```

Consultar un fallo del backend:

```powershell
docker compose logs --tail 100 api
```

Para trabajar con actualización rápida, ejecuta en tres terminales diferentes:

```powershell
corepack pnpm dev:infra
corepack pnpm dev:api
corepack pnpm dev:web
```
