# Collab Modeler Local Agent

Agente local para generar y ejecutar aplicaciones Flutter desde la web de Collab Modeler.

## Requisitos

- Node.js 22+
- Flutter SDK estable (>= 3.x) en `PATH`
- Android SDK con Platform Tools y `adb` en `PATH`
- Dispositivo Android con depuración USB habilitada

## Instalación

```powershell
cd scripts/local-agent
npm install
npm run build
```

O desde la raíz del monorepo:

```powershell
pnpm agent:install
```

## Uso

```powershell
# Desde la raíz del monorepo
pnpm agent:start

# O directamente
cd scripts/local-agent
npm start
```

El agente escucha en `http://127.0.0.1:9876` (solo loopback, nunca expuesto a la red).

## Flujo

1. En la web de Collab Modeler, abre un diagrama y haz clic en **Generar App Móvil**.
2. La web verifica que el agente local esté activo.
3. Obtiene la especificación firmada (HMAC-SHA256) con nonce de un solo uso del backend.
4. Envía la especificación + ZIP Flutter al agente local.
5. El agente valida la firma, consume el nonce, extrae el proyecto y ejecuta `flutter pub get`.
6. Puedes seleccionar un dispositivo conectado y ejecutar `flutter run` o `flutter build apk --release`.

## Seguridad

- **Solo loopback**: el servidor se vincula a `127.0.0.1`, nunca a `0.0.0.0`.
- **Validación de origen**: solo acepta solicitudes desde `http://localhost:5173` o `http://127.0.0.1:5173`.
- **Firma HMAC-SHA256**: verifica que la especificación no haya sido manipulada.
- **Nonce de un solo uso**: cada especificación solo se puede usar una vez.
- **Comandos cerrados**: solo ejecuta `flutter pub get`, `flutter analyze`, `flutter run`, `flutter build apk --release`, `adb devices` y `adb reverse tcp:8080 tcp:8080`.
- **Sanitización de rutas**: nunca acepta rutas con `..`, rutas de sistema o rutas fuera del directorio del usuario.

## Conexión del dispositivo Android

Para conectar un Samsung A56 u otro dispositivo Android al backend local:

**Por USB (recomendado):**
```bash
adb reverse tcp:8080 tcp:8080
```
El agente ejecuta esto automáticamente antes de `flutter run`.
La app Flutter usará `http://localhost:8080` como API base.

**Por Wi-Fi:**
1. PC y teléfono en la misma red Wi-Fi.
2. Obtén la IP privada: `ipconfig` (Windows) o `ip addr` (Linux).
3. Al generar, usa la IP como API base: `http://192.168.1.XX:8080`.
4. Verifica firewall: permite el puerto 8080 en red privada.

## Variables de entorno

| Variable | Descripción | Predeterminado |
|---|---|---|
| `AGENT_PORT` | Puerto del agente | `9876` |
| `AGENT_SIGNING_KEY` | Clave HMAC-SHA256 | Clave compartida con el backend |

## Pruebas

```powershell
cd scripts/local-agent
npm test
```
