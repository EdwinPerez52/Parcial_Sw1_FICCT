# Límites de módulos

El repositorio se organiza por unidades desplegables y por responsabilidades operativas. Cada módulo debe poder entenderse, probarse y entregarse sin conocer los detalles internos de los demás.

```
Backend-web/       Spring Boot: API, dominio, persistencia y generación de artefactos
Frontend-web/      React: interfaz, estado de cliente y adaptadores HTTP/WebSocket
mobile-flutter/    Flutter offline-first y su herramienta local de generación
  tools/local-agent/  Servicio local opcional para materializar y ejecutar Flutter
infra/
  terraform/    Recursos cloud, estado remoto y ambientes
scripts/        Automatizaciones de desarrollo y validación, sin lógica de negocio
docs/
  development/  Guías operativas y prompts internos
  product/      Hoja de ruta y decisiones de producto
```

## Reglas de dependencia

- Las aplicaciones no importan código fuente entre sí. Se integran mediante HTTP, WebSocket, archivos versionados o contratos documentados.
- `Backend-web` se organiza por capacidad de negocio (`auth`, `diagram`, `access`, `comment`, `generation`, etc.). Un controlador delega al servicio de su capacidad; repositorios y entidades no se exponen a otras capas HTTP.
- `Frontend-web` y `mobile-flutter` mantienen sus contratos remotos en adaptadores propios. No duplican reglas de autorización que pertenecen a la API.
- `mobile-flutter/tools/local-agent` es una herramienta local aislada: nunca contiene credenciales persistentes ni es una dependencia de arranque de las aplicaciones.
- `infra` no contiene secretos ni artefactos construidos. Sus variables se resuelven desde el entorno de despliegue.
- `scripts` solo orquesta comandos de módulos; no debe convertirse en una segunda fuente de lógica de aplicación.

## Higiene del repositorio

No se versionan salidas de navegador, ZIP generados, directorios de trabajo temporales, dependencias, builds ni estado de herramientas. Los artefactos de CI se publican desde GitHub Actions y los entregables generados se almacenan fuera del árbol fuente.

Antes de añadir una carpeta nueva, se debe decidir si pertenece al backend, frontend, móvil, infraestructura (`infra`), automatización (`scripts`) o documentación (`docs`).
