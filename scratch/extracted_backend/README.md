# prueba

Backend Spring Boot generado automáticamente desde **Collab Modeler** (revisión 186).

## Características
- **Java 21** y **Spring Boot 3.5.16**.
- **PostgreSQL** con migraciones **Flyway**.
- **JPA / Hibernate** con herencia `JOINED`, relaciones 1:1, 1:N, N:M con lado propietario y enumeraciones como `STRING`.
- **DTOs separados** de entrada y salida con **Bean Validation** y **Mappers**.
- **CRUD completo** con actualización para todas las entidades.
- **OpenAPI 3.0.3** y Swagger UI en `/swagger-ui.html`.
- **Autenticación completa**: registro con correo/contraseña, JWT de acceso y refresco, roles `ADMIN` y `USER`. El primer usuario registrado obtiene automáticamente el rol `ADMIN`.
- Esquema de autenticación aislado en tablas `_app_auth_*` para evitar colisiones con el modelo de dominio.
- **Dockerfile multi-stage** y **Docker Compose**.
- Trazabilidad hacia el diagrama UML con anotación `@ModelElement` y `model-traceability.json`.

## Ejecución con Docker Compose
```bash
docker compose up --build
```
La API estará disponible en `http://localhost:8080` y la base de datos en `localhost:5432`.

## Ejecución local con Maven
1. Inicia PostgreSQL (o `docker compose up -d db`).
2. Configura las variables si es necesario:
```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/app
export DATABASE_USER=app
export DATABASE_PASSWORD=app
```
3. Ejecuta la aplicación:
```bash
mvn spring-boot:run
```

## Pruebas
```bash
mvn test
```
Las pruebas de integración ejecutan PostgreSQL mediante Testcontainers.

## Autenticación
- Registro: `POST /api/auth/register` con JSON `{"email":"admin@example.com","password":"password123","fullName":"Administrador"}`. El primer usuario registrado es `ROLE_ADMIN`.
- Inicio de sesión: `POST /api/auth/login` con JSON `{"email":"...","password":"..."}`.
- Refresco de token: `POST /api/auth/refresh` con `{"refreshToken":"..."}`.
- Perfil: `GET /api/auth/me` con cabecera `Authorization: Bearer <accessToken>`.
