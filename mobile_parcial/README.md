# Ventas Móvil

Aplicación móvil y multiplataforma Flutter generada automáticamente desde **Collab Modeler** (revisión 1).

## Arquitectura por Capas
- **`core/`**: Cliente HTTP autenticado con reintento y refresco de tokens, manejo uniforme de errores, configuración de URL de servidor persistente, tema accesible Material 3 y componentes reutilizables.
- **`data/`**: Modelos fuertemente tipados con serialización JSON para entidades y enumeraciones, y repositorios que consumen el backend generado.
- **`presentation/`**: Gestión de estado reactiva basada en `ChangeNotifier`, pantallas de autenticación, configuración de red, dashboard de entidades y flujos CRUD completos adaptables con búsqueda, paginación, formularios con validación y selectores de relaciones.

## Requisitos
- Flutter SDK 3.x estable (`flutter doctor -v`).
- Android SDK Platform-Tools con `adb` en `PATH`.
- Backend de Collab Modeler ejecutándose en el puerto 8080 (`http://localhost:8080`).

## Conexión Inmediata al Backend en localhost:8080

### 1. Dispositivo Android Físico conectado por USB (por ejemplo Samsung A56)
Conecta el teléfono por USB con Depuración USB habilitada y ejecuta en la terminal de la computadora:
```bash
adb reverse tcp:8080 tcp:8080
```
Esto redirige el puerto 8080 del teléfono al backend que corre en la computadora. Luego inicia la app:
```bash
flutter run -d <DEVICE_ID> --dart-define=API_BASE_URL=http://localhost:8080
```

### 2. Flutter Web o Desktop (Windows / macOS / Linux)
```bash
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080
# o en Windows Desktop:
flutter run -d windows --dart-define=API_BASE_URL=http://localhost:8080
```

### 3. Conexión alternativa por IP Local (Wi-Fi)
Si prefieres no usar `adb reverse` o pruebas por red inalámbrica:
1. Obtén la IP privada de tu computadora (ejemplo `192.168.1.50`).
2. Inicia la app especificando la IP:
```bash
flutter run --dart-define=API_BASE_URL=http://192.168.1.50:8080
```
3. O abre la aplicación, toca el ícono de engranaje en la pantalla de inicio de sesión y actualiza la URL del servidor en tiempo de ejecución.

## Comandos de Calidad y Pruebas
```bash
flutter pub get
flutter analyze
flutter test
```

## Compilación de APK Release
```bash
flutter build apk --release --dart-define=API_BASE_URL=http://localhost:8080
```

## IA local y privacidad

El asistente ofrece texto, voz y fotografía. Texto y OCR se procesan localmente; OCR usa ML Kit Text Recognition para alfabeto latino. La voz usa el reconocedor instalado por Android. En el Samsung, instala español en el reconocimiento de voz sin conexión para no depender de red. No necesitas instalar un LLM ni una aplicación de IA adicional.

Todas las mutaciones se muestran como propuesta. Cancelar no escribe SQLite ni la outbox; confirmar reutiliza los repositorios offline-first y queda pendiente de sincronización. Las imágenes y transcripciones no se guardan ni se imprimen en logs. El proveedor remoto es opcional y nunca evita la validación o confirmación local.
