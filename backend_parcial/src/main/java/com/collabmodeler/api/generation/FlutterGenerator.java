package com.collabmodeler.api.generation;

import com.collabmodeler.api.diagram.DiagramDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class FlutterGenerator {
    private final ObjectMapper mapper;
    private final ModelValidator validator;

    @org.springframework.beans.factory.annotation.Autowired
    public FlutterGenerator(ObjectMapper mapper, ModelValidator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public FlutterGenerator(ObjectMapper mapper) {
        this(mapper, new ModelValidator());
    }

    public Map<String, String> generateFiles(DiagramDocument diagram, String openapiYaml, String appTitle) {
        validator.validate(diagram, "com.generated", "flutter-app");

        String title = appTitle != null && !appTitle.isBlank() ? appTitle : diagram.name() + " App";
        String pubName = sqlName(diagram.name()).replace('-', '_');
        if (pubName.startsWith("_")) pubName = "app" + pubName;

        Map<String, String> files = new LinkedHashMap<>();

        // 1. Build and Config
        files.put("pubspec.yaml", pubspec(pubName, title));
        files.put("analysis_options.yaml", analysisOptions());
        files.put("README.md", readme(diagram, title, pubName));

        // 2. Core Architecture
        files.put("lib/core/config/app_config.dart", appConfig());
        files.put("lib/core/api/api_exception.dart", apiException());
        files.put("lib/core/storage/secure_storage_service.dart", secureStorageService());
        files.put("lib/core/database/app_database.dart", appDatabase());
        files.put("lib/core/sync/outbox_service.dart", outboxService(pubName));
        files.put("lib/core/sync/sync_service.dart", syncService(pubName));
        files.put("lib/core/api/api_client.dart", apiClient(pubName));
        files.put("lib/core/theme/app_theme.dart", appTheme());
        files.put("lib/core/i18n/app_strings.dart", appStrings(diagram));
        files.put("lib/core/widgets/async_state_view.dart", asyncStateView(pubName));
        files.put("lib/core/widgets/confirm_dialog.dart", confirmDialog(pubName));
        files.put("lib/core/widgets/relation_picker.dart", relationPicker());
        files.put("lib/core/widgets/sync_status_badge.dart", syncStatusBadge(pubName));

        // 3. Auth Data & State
        files.put("lib/data/models/auth_models.dart", authModels());
        files.put("lib/data/repositories/auth_repository.dart", authRepository(pubName));
        files.put("lib/presentation/state/auth_provider.dart", authProvider(pubName));
        files.put("lib/presentation/screens/auth/login_screen.dart", loginScreen(pubName));
        files.put("lib/presentation/screens/auth/register_screen.dart", registerScreen(pubName));
        files.put("lib/presentation/screens/settings/server_settings_screen.dart", serverSettingsScreen(pubName));
        files.put("lib/presentation/screens/conflicts/conflict_resolution_screen.dart", conflictResolutionScreen(pubName));

        // 4. Enumerations
        for (var en : diagram.enumerations()) {
            files.put("lib/data/models/" + sqlName(en.name()) + ".dart", enumModel(en));
        }

        // 5. Domain Entities (Models, Repositories, Providers, Screens)
        for (var item : diagram.classes()) {
            String filePrefix = sqlName(item.name());

            files.put("lib/data/models/" + filePrefix + ".dart", entityModel(diagram, item, pubName));
            files.put("lib/data/repositories/" + filePrefix + "_repository.dart", entityRepository(diagram, item, pubName));
            files.put("lib/presentation/state/" + filePrefix + "_provider.dart", entityProvider(diagram, item, pubName));
            files.put("lib/presentation/screens/" + filePrefix + "/" + filePrefix + "_list_screen.dart", entityListScreen(diagram, item, pubName));
            files.put("lib/presentation/screens/" + filePrefix + "/" + filePrefix + "_detail_screen.dart", entityDetailScreen(diagram, item, pubName));
            files.put("lib/presentation/screens/" + filePrefix + "/" + filePrefix + "_form_screen.dart", entityFormScreen(diagram, item, pubName));
        }

        // 6. Home Dashboard & Main Entrypoint
        files.put("lib/presentation/screens/home/home_screen.dart", homeScreen(diagram, title, pubName));
        files.put("lib/main.dart", mainEntrypoint(diagram, title, pubName));

        // 7. Tests
        files.put("test/model_test.dart", modelTest(diagram, pubName));
        files.put("test/widget_test.dart", widgetTest(pubName));
        files.put("test/offline_sync_test.dart", offlineSyncTest(diagram, pubName));

        return files;
    }

    public byte[] generateZip(DiagramDocument diagram, String openapiYaml, String appTitle) {
        Map<String, String> files = generateFiles(diagram, openapiYaml, appTitle);
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipStream = new ZipOutputStream(byteStream)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String safePath = sanitizeZipPath(entry.getKey());
                ZipEntry zipEntry = new ZipEntry(safePath);
                zipStream.putNextEntry(zipEntry);
                zipStream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zipStream.closeEntry();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Error al comprimir el proyecto Flutter", exception);
        }
        return byteStream.toByteArray();
    }

    public void writeToDirectory(DiagramDocument diagram, String openapiYaml, String appTitle, Path outputDir) throws IOException {
        Map<String, String> files = generateFiles(diagram, openapiYaml, appTitle);
        for (var entry : files.entrySet()) {
            Path targetFile = outputDir.resolve(entry.getKey()).normalize();
            if (!targetFile.startsWith(outputDir.normalize())) {
                throw new SecurityException("Intento de path traversal detectado: " + entry.getKey());
            }
            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, entry.getValue(), StandardCharsets.UTF_8);
        }
    }

    // =========================================================================
    // 1. CONFIG & BUILD TEMPLATES
    // =========================================================================

    private String pubspec(String pubName, String title) {
        return """
        name: %s
        description: %s generada automáticamente desde Collab Modeler
        publish_to: 'none'
        version: 1.0.0+1

        environment:
          sdk: '>=3.0.0 <4.0.0'

        dependencies:
          flutter:
            sdk: flutter
          flutter_localizations:
            sdk: flutter
          intl: any
          http: any
          shared_preferences: any
          sqflite: any
          sqflite_common_ffi: any
          path: any
          flutter_secure_storage: any
          uuid: any

        dev_dependencies:
          flutter_test:
            sdk: flutter
          flutter_lints: any

        flutter:
          uses-material-design: true
        """.formatted(pubName, title);
    }

    private String analysisOptions() {
        return """
        include: package:flutter_lints/flutter.yaml

        linter:
          rules:
            avoid_print: false
            use_build_context_synchronously: false
            prefer_const_constructors: true
            prefer_const_literals_to_create_immutables: true
        """;
    }

    private String readme(DiagramDocument diagram, String title, String pubName) {
        return """
        # %s

        Aplicación móvil y multiplataforma Flutter generada automáticamente desde **Collab Modeler** (revisión %d).

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
        """.formatted(title, diagram.revision());
    }

    // =========================================================================
    // 2. CORE ARCHITECTURE TEMPLATES
    // =========================================================================

    private String appConfig() {
        return """
        import 'package:shared_preferences/shared_preferences.dart';

        class AppConfig {
          static const String defaultApiUrl = String.fromEnvironment(
            'API_BASE_URL',
            defaultValue: 'http://localhost:8080',
          );

          static const String _storageKey = 'collab_modeler_api_base_url';

          static Future<String> getBaseUrl() async {
            try {
              final prefs = await SharedPreferences.getInstance();
              final saved = prefs.getString(_storageKey);
              if (saved != null && saved.trim().isNotEmpty) {
                return saved.trim().replaceAll(RegExp(r'/+$'), '');
              }
            } catch (_) {}
            return defaultApiUrl.replaceAll(RegExp(r'/+$'), '');
          }

          static Future<void> setBaseUrl(String url) async {
            final prefs = await SharedPreferences.getInstance();
            await prefs.setString(_storageKey, url.trim().replaceAll(RegExp(r'/+$'), ''));
          }

          static Future<void> resetBaseUrl() async {
            final prefs = await SharedPreferences.getInstance();
            await prefs.remove(_storageKey);
          }
        }
        """;
    }

    private String apiException() {
        return """
        class ApiException implements Exception {
          final int statusCode;
          final String message;
          final Map<String, dynamic>? errors;

          ApiException({
            required this.statusCode,
            required this.message,
            this.errors,
          });

          @override
          String toString() => message;
        }
        """;
    }

    private String apiClient(String pubName) {
        return """
        import 'dart:convert';
        import 'package:http/http.dart' as http;
        import 'package:%s/core/config/app_config.dart';
        import 'package:%s/core/api/api_exception.dart';
        import 'package:%s/core/storage/secure_storage_service.dart';

        class ApiClient {
          static final ApiClient instance = ApiClient._();
          ApiClient._();

          final SecureStorageService _secureStorage = SecureStorageService.instance;

          String? _accessToken;
          String? _refreshToken;

          Future<void> init() async {
            try {
              _accessToken = await _secureStorage.read('auth_access_token');
              _refreshToken = await _secureStorage.read('auth_refresh_token');
            } catch (_) {}
          }

          bool get isAuthenticated => _accessToken != null && _accessToken!.isNotEmpty;
          String? get token => _accessToken;

          Future<void> saveTokens({required String accessToken, required String refreshToken}) async {
            _accessToken = accessToken;
            _refreshToken = refreshToken;
            await _secureStorage.write('auth_access_token', accessToken);
            await _secureStorage.write('auth_refresh_token', refreshToken);
          }

          Future<void> clearTokens() async {
            _accessToken = null;
            _refreshToken = null;
            await _secureStorage.delete('auth_access_token');
            await _secureStorage.delete('auth_refresh_token');
          }

          Future<Map<String, String>> _headers([Map<String, String>? extra]) async {
            final headers = <String, String>{
              'Content-Type': 'application/json; charset=utf-8',
              'Accept': 'application/json',
            };
            if (_accessToken != null && _accessToken!.isNotEmpty) {
              headers['Authorization'] = 'Bearer $_accessToken';
            }
            if (extra != null) {
              headers.addAll(extra);
            }
            return headers;
          }

          Future<dynamic> get(String path, {Map<String, String>? queryParams, Map<String, String>? headers}) async {
            final baseUrl = await AppConfig.getBaseUrl();
            var uri = Uri.parse('$baseUrl$path');
            if (queryParams != null && queryParams.isNotEmpty) {
              uri = uri.replace(queryParameters: queryParams);
            }
            final response = await http.get(uri, headers: await _headers(headers));
            return _handleResponse(response, () => get(path, queryParams: queryParams, headers: headers));
          }

          Future<dynamic> post(String path, {dynamic body, Map<String, String>? headers}) async {
            final baseUrl = await AppConfig.getBaseUrl();
            final uri = Uri.parse('$baseUrl$path');
            final response = await http.post(
              uri,
              headers: await _headers(headers),
              body: body != null ? jsonEncode(body) : null,
            );
            return _handleResponse(response, () => post(path, body: body, headers: headers));
          }

          Future<dynamic> put(String path, {dynamic body, Map<String, String>? headers}) async {
            final baseUrl = await AppConfig.getBaseUrl();
            final uri = Uri.parse('$baseUrl$path');
            final response = await http.put(
              uri,
              headers: await _headers(headers),
              body: body != null ? jsonEncode(body) : null,
            );
            return _handleResponse(response, () => put(path, body: body, headers: headers));
          }

          Future<void> delete(String path, {Map<String, String>? headers}) async {
            final baseUrl = await AppConfig.getBaseUrl();
            final uri = Uri.parse('$baseUrl$path');
            final response = await http.delete(uri, headers: await _headers(headers));
            if (response.statusCode == 204 || response.statusCode == 200) {
              return;
            }
            await _handleResponse(response, () => delete(path, headers: headers));
          }

          Future<dynamic> _handleResponse(http.Response response, Future<dynamic> Function() retry) async {
            if (response.statusCode >= 200 && response.statusCode < 300) {
              if (response.body.isEmpty) return null;
              return jsonDecode(utf8.decode(response.bodyBytes));
            }

            if (response.statusCode == 401 && _refreshToken != null) {
              final refreshed = await _tryRefreshToken();
              if (refreshed) {
                return retry();
              }
            }

            String message = 'Error ${response.statusCode}';
            Map<String, dynamic>? errors;
            try {
              final body = jsonDecode(utf8.decode(response.bodyBytes));
              if (body is Map<String, dynamic>) {
                message = body['message'] ?? body['error'] ?? body['detail'] ?? message;
                if (body['validationErrors'] is Map<String, dynamic>) {
                  errors = body['validationErrors'];
                }
              }
            } catch (_) {}

            throw ApiException(statusCode: response.statusCode, message: message, errors: errors);
          }

          Future<bool> _tryRefreshToken() async {
            if (_refreshToken == null) return false;
            try {
              final baseUrl = await AppConfig.getBaseUrl();
              final uri = Uri.parse('$baseUrl/api/auth/refresh');
              final res = await http.post(
                uri,
                headers: {'Content-Type': 'application/json'},
                body: jsonEncode({'refreshToken': _refreshToken}),
              );
              if (res.statusCode == 200) {
                final data = jsonDecode(utf8.decode(res.bodyBytes));
                if (data is Map<String, dynamic> && data['accessToken'] != null) {
                  await saveTokens(
                    accessToken: data['accessToken'],
                    refreshToken: data['refreshToken'] ?? _refreshToken!,
                  );
                  return true;
                }
              }
            } catch (_) {}
            await clearTokens();
            return false;
          }
        }
        """.formatted(pubName, pubName, pubName);
    }

    private String secureStorageService() {
        return """
        import 'package:flutter_secure_storage/flutter_secure_storage.dart';
        import 'package:shared_preferences/shared_preferences.dart';

        class SecureStorageService {
          static final SecureStorageService instance = SecureStorageService._();
          SecureStorageService._();

          final FlutterSecureStorage _secureStorage = const FlutterSecureStorage();
          final Map<String, String> _memoryFallback = {};

          Future<void> write(String key, String value) async {
            _memoryFallback[key] = value;
            try {
              await _secureStorage.write(key: key, value: value);
            } catch (_) {
              try {
                final prefs = await SharedPreferences.getInstance();
                await prefs.setString('sec_$key', value);
              } catch (_) {}
            }
          }

          Future<String?> read(String key) async {
            try {
              final val = await _secureStorage.read(key: key);
              if (val != null && val.isNotEmpty) return val;
            } catch (_) {}
            if (_memoryFallback.containsKey(key)) {
              return _memoryFallback[key];
            }
            try {
              final prefs = await SharedPreferences.getInstance();
              return prefs.getString('sec_$key');
            } catch (_) {
              return null;
            }
          }

          Future<void> delete(String key) async {
            _memoryFallback.remove(key);
            try {
              await _secureStorage.delete(key: key);
            } catch (_) {}
            try {
              final prefs = await SharedPreferences.getInstance();
              await prefs.remove('sec_$key');
            } catch (_) {}
          }
        }
        """;
    }

    private String appDatabase() {
        return """
        import 'dart:convert';
        import 'dart:io' show Platform;
        import 'package:flutter/foundation.dart' show kIsWeb;
        import 'package:path/path.dart';
        import 'package:sqflite_common_ffi/sqflite_ffi.dart';

        class AppDatabase {
          static final AppDatabase instance = AppDatabase._();
          AppDatabase._();

          Database? _db;

          Future<Database> get database async {
            if (_db != null && _db!.isOpen) return _db!;
            _db = await _initDatabase();
            return _db!;
          }

          Future<Database> _initDatabase() async {
            if (!kIsWeb && (Platform.isWindows || Platform.isLinux || Platform.isMacOS)) {
              sqfliteFfiInit();
              databaseFactory = databaseFactoryFfi;
            }

            final dbPath = await getDatabasesPath();
            final path = join(dbPath, 'collab_modeler_offline.db');

            return await openDatabase(
              path,
              version: 1,
              onCreate: (db, version) async {
                await db.execute('''
                  CREATE TABLE cached_entities (
                    entity_type TEXT NOT NULL,
                    id TEXT NOT NULL,
                    data TEXT NOT NULL,
                    version INTEGER NOT NULL DEFAULT 1,
                    is_deleted INTEGER NOT NULL DEFAULT 0,
                    sync_status TEXT NOT NULL DEFAULT 'synced',
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY (entity_type, id)
                  )
                ''');

                await db.execute('''
                  CREATE INDEX idx_cached_entities_type ON cached_entities(entity_type)
                ''');

                await db.execute('''
                  CREATE INDEX idx_cached_entities_sync ON cached_entities(entity_type, sync_status)
                ''');

                await db.execute('''
                  CREATE TABLE outbox_operations (
                    id TEXT PRIMARY KEY,
                    entity TEXT NOT NULL,
                    record_id TEXT NOT NULL,
                    action TEXT NOT NULL,
                    base_version INTEGER NOT NULL DEFAULT 0,
                    created_at TEXT NOT NULL,
                    payload TEXT,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    error_message TEXT
                  )
                ''');

                await db.execute('''
                  CREATE INDEX idx_outbox_status_created ON outbox_operations(status, created_at)
                ''');

                await db.execute('''
                  CREATE TABLE conflict_records (
                    id TEXT PRIMARY KEY,
                    operation_id TEXT NOT NULL,
                    entity TEXT NOT NULL,
                    record_id TEXT NOT NULL,
                    base_version INTEGER NOT NULL DEFAULT 0,
                    server_version INTEGER NOT NULL DEFAULT 0,
                    local_payload TEXT NOT NULL,
                    server_payload TEXT NOT NULL,
                    conflicting_fields TEXT NOT NULL,
                    created_at TEXT NOT NULL,
                    status TEXT NOT NULL DEFAULT 'OPEN'
                  )
                ''');

                await db.execute('''
                  CREATE INDEX idx_conflicts_status ON conflict_records(status)
                ''');
              },
            );
          }

          Future<List<Map<String, dynamic>>> getCachedEntities(
            String entityType, {
            bool includeDeleted = false,
          }) async {
            final db = await database;
            final where = includeDeleted
                ? 'entity_type = ?'
                : 'entity_type = ? AND is_deleted = 0';
            final rows = await db.query(
              'cached_entities',
              where: where,
              whereArgs: [entityType],
              orderBy: 'updated_at DESC',
            );

            return rows.map((r) {
              final jsonMap = jsonDecode(r['data'] as String) as Map<String, dynamic>;
              jsonMap['_syncStatus'] = r['sync_status'];
              jsonMap['_version'] = r['version'];
              jsonMap['_isDeleted'] = (r['is_deleted'] as int) == 1;
              return jsonMap;
            }).toList();
          }

          Future<Map<String, dynamic>?> getCachedEntity(String entityType, String id) async {
            final db = await database;
            final rows = await db.query(
              'cached_entities',
              where: 'entity_type = ? AND id = ?',
              whereArgs: [entityType, id],
              limit: 1,
            );
            if (rows.isEmpty) return null;
            final r = rows.first;
            final jsonMap = jsonDecode(r['data'] as String) as Map<String, dynamic>;
            jsonMap['_syncStatus'] = r['sync_status'];
            jsonMap['_version'] = r['version'];
            jsonMap['_isDeleted'] = (r['is_deleted'] as int) == 1;
            return jsonMap;
          }

          Future<void> saveCachedEntity(
            String entityType,
            String id,
            Map<String, dynamic> data, {
            int version = 1,
            String syncStatus = 'synced',
            bool isDeleted = false,
          }) async {
            final db = await database;
            final now = DateTime.now().toIso8601String();
            await db.insert(
              'cached_entities',
              {
                'entity_type': entityType,
                'id': id,
                'data': jsonEncode(data),
                'version': version,
                'is_deleted': isDeleted ? 1 : 0,
                'sync_status': syncStatus,
                'updated_at': now,
              },
              conflictAlgorithm: ConflictAlgorithm.replace,
            );
          }

          Future<void> updateCachedEntityId(
            String entityType,
            String oldId,
            String newId,
            Map<String, dynamic> newData, {
            int version = 1,
            String syncStatus = 'synced',
          }) async {
            final db = await database;
            await db.transaction((txn) async {
              await txn.delete(
                'cached_entities',
                where: 'entity_type = ? AND id = ?',
                whereArgs: [entityType, oldId],
              );
              final now = DateTime.now().toIso8601String();
              await txn.insert(
                'cached_entities',
                {
                  'entity_type': entityType,
                  'id': newId,
                  'data': jsonEncode(newData),
                  'version': version,
                  'is_deleted': 0,
                  'sync_status': syncStatus,
                  'updated_at': now,
                },
                conflictAlgorithm: ConflictAlgorithm.replace,
              );
            });
          }

          Future<void> removeCachedEntity(String entityType, String id) async {
            final db = await database;
            await db.delete(
              'cached_entities',
              where: 'entity_type = ? AND id = ?',
              whereArgs: [entityType, id],
            );
          }

          Future<void> clearAll() async {
            final db = await database;
            await db.delete('cached_entities');
            await db.delete('outbox_operations');
            await db.delete('conflict_records');
          }

          Future<void> close() async {
            if (_db != null && _db!.isOpen) {
              await _db!.close();
              _db = null;
            }
          }
        }
        """;
    }

    private String outboxService(String pubName) {
        return """
        import 'dart:convert';
        import 'package:sqflite/sqflite.dart';
        import 'package:uuid/uuid.dart';
        import 'package:%s/core/database/app_database.dart';

        class OutboxOperation {
          final String id;
          final String entity;
          final String recordId;
          final String action;
          final int baseVersion;
          final DateTime createdAt;
          final Map<String, dynamic>? payload;
          final String status;
          final int retryCount;
          final String? errorMessage;

          OutboxOperation({
            required this.id,
            required this.entity,
            required this.recordId,
            required this.action,
            required this.baseVersion,
            required this.createdAt,
            this.payload,
            this.status = 'PENDING',
            this.retryCount = 0,
            this.errorMessage,
          });

          Map<String, dynamic> toMap() {
            return {
              'id': id,
              'entity': entity,
              'record_id': recordId,
              'action': action,
              'base_version': baseVersion,
              'created_at': createdAt.toIso8601String(),
              'payload': payload != null ? jsonEncode(payload) : null,
              'status': status,
              'retry_count': retryCount,
              'error_message': errorMessage,
            };
          }

          factory OutboxOperation.fromMap(Map<String, dynamic> map) {
            return OutboxOperation(
              id: map['id'] as String,
              entity: map['entity'] as String,
              recordId: map['record_id'] as String,
              action: map['action'] as String,
              baseVersion: map['base_version'] as int? ?? 0,
              createdAt: DateTime.parse(map['created_at'] as String),
              payload: map['payload'] != null
                  ? jsonDecode(map['payload'] as String) as Map<String, dynamic>
                  : null,
              status: map['status'] as String? ?? 'PENDING',
              retryCount: map['retry_count'] as int? ?? 0,
              errorMessage: map['error_message'] as String?,
            );
          }
        }

        class OutboxService {
          static final OutboxService instance = OutboxService._();
          OutboxService._();

          final AppDatabase _db = AppDatabase.instance;
          static const Uuid _uuid = Uuid();

          Future<OutboxOperation> enqueueCreate({
            required String entity,
            required String recordId,
            required Map<String, dynamic> payload,
          }) async {
            final db = await _db.database;
            final opId = _uuid.v4();
            final now = DateTime.now();

            final op = OutboxOperation(
              id: opId,
              entity: entity,
              recordId: recordId,
              action: 'CREATE',
              baseVersion: 0,
              createdAt: now,
              payload: payload,
              status: 'PENDING',
            );

            await db.transaction((txn) async {
              await txn.insert(
                'cached_entities',
                {
                  'entity_type': entity,
                  'id': recordId,
                  'data': jsonEncode(payload),
                  'version': 1,
                  'is_deleted': 0,
                  'sync_status': 'pending_create',
                  'updated_at': now.toIso8601String(),
                },
                conflictAlgorithm: ConflictAlgorithm.replace,
              );

              await txn.insert(
                'outbox_operations',
                op.toMap(),
                conflictAlgorithm: ConflictAlgorithm.replace,
              );
            });

            return op;
          }

          Future<OutboxOperation> enqueueUpdate({
            required String entity,
            required String recordId,
            required Map<String, dynamic> payload,
            required int baseVersion,
          }) async {
            final db = await _db.database;
            final opId = _uuid.v4();
            final now = DateTime.now();

            final op = OutboxOperation(
              id: opId,
              entity: entity,
              recordId: recordId,
              action: 'UPDATE',
              baseVersion: baseVersion,
              createdAt: now,
              payload: payload,
              status: 'PENDING',
            );

            await db.transaction((txn) async {
              await txn.insert(
                'cached_entities',
                {
                  'entity_type': entity,
                  'id': recordId,
                  'data': jsonEncode(payload),
                  'version': baseVersion + 1,
                  'is_deleted': 0,
                  'sync_status': 'pending_update',
                  'updated_at': now.toIso8601String(),
                },
                conflictAlgorithm: ConflictAlgorithm.replace,
              );

              await txn.insert(
                'outbox_operations',
                op.toMap(),
                conflictAlgorithm: ConflictAlgorithm.replace,
              );
            });

            return op;
          }

          Future<OutboxOperation> enqueueDelete({
            required String entity,
            required String recordId,
            required int baseVersion,
          }) async {
            final db = await _db.database;
            final opId = _uuid.v4();
            final now = DateTime.now();

            final op = OutboxOperation(
              id: opId,
              entity: entity,
              recordId: recordId,
              action: 'DELETE',
              baseVersion: baseVersion,
              createdAt: now,
              status: 'PENDING',
            );

            await db.transaction((txn) async {
              await txn.update(
                'cached_entities',
                {
                  'is_deleted': 1,
                  'sync_status': 'pending_delete',
                  'updated_at': now.toIso8601String(),
                },
                where: 'entity_type = ? AND id = ?',
                whereArgs: [entity, recordId],
              );

              await txn.insert(
                'outbox_operations',
                op.toMap(),
                conflictAlgorithm: ConflictAlgorithm.replace,
              );
            });

            return op;
          }

          Future<List<OutboxOperation>> getPendingOperations() async {
            final db = await _db.database;
            final rows = await db.query(
              'outbox_operations',
              where: 'status = ?',
              whereArgs: ['PENDING'],
              orderBy: 'created_at ASC',
            );
            return rows.map((r) => OutboxOperation.fromMap(r)).toList();
          }

          Future<int> getPendingCount() async {
            final db = await _db.database;
            final res = await db.rawQuery(
              "SELECT COUNT(*) as count FROM outbox_operations WHERE status = 'PENDING'",
            );
            return Sqflite.firstIntValue(res) ?? 0;
          }

          Future<void> markCompleted(String operationId) async {
            final db = await _db.database;
            await db.delete(
              'outbox_operations',
              where: 'id = ?',
              whereArgs: [operationId],
            );
          }

          Future<void> markConflict(String operationId, String errorMessage) async {
            final db = await _db.database;
            await db.update(
              'outbox_operations',
              {
                'status': 'CONFLICT',
                'error_message': errorMessage,
              },
              where: 'id = ?',
              whereArgs: [operationId],
            );
          }

          Future<void> markFailed(String operationId, String error) async {
            final db = await _db.database;
            await db.rawUpdate(
              'UPDATE outbox_operations SET retry_count = retry_count + 1, error_message = ? WHERE id = ?',
              [error, operationId],
            );
          }
        }
        """.formatted(pubName);
    }

    private String syncService(String pubName) {
        return """
        import 'dart:convert';
        import 'dart:io';
        import 'package:flutter/foundation.dart';
        import 'package:http/http.dart' as http;
        import 'package:sqflite/sqflite.dart';
        import 'package:uuid/uuid.dart';
        import 'package:%s/core/api/api_client.dart';
        import 'package:%s/core/config/app_config.dart';
        import 'package:%s/core/database/app_database.dart';
        import 'package:%s/core/sync/outbox_service.dart';

        class ConflictRecord {
          final String id;
          final String operationId;
          final String entity;
          final String recordId;
          final int baseVersion;
          final int serverVersion;
          final Map<String, dynamic> localPayload;
          final Map<String, dynamic> serverPayload;
          final List<String> conflictingFields;
          final DateTime createdAt;
          final String status;

          ConflictRecord({
            required this.id,
            required this.operationId,
            required this.entity,
            required this.recordId,
            required this.baseVersion,
            required this.serverVersion,
            required this.localPayload,
            required this.serverPayload,
            required this.conflictingFields,
            required this.createdAt,
            this.status = 'OPEN',
          });

          Map<String, dynamic> toMap() {
            return {
              'id': id,
              'operation_id': operationId,
              'entity': entity,
              'record_id': recordId,
              'base_version': baseVersion,
              'server_version': serverVersion,
              'local_payload': jsonEncode(localPayload),
              'server_payload': jsonEncode(serverPayload),
              'conflicting_fields': conflictingFields.join(','),
              'created_at': createdAt.toIso8601String(),
              'status': status,
            };
          }

          factory ConflictRecord.fromMap(Map<String, dynamic> map) {
            return ConflictRecord(
              id: map['id'] as String,
              operationId: map['operation_id'] as String,
              entity: map['entity'] as String,
              recordId: map['record_id'] as String,
              baseVersion: map['base_version'] as int? ?? 0,
              serverVersion: map['server_version'] as int? ?? 0,
              localPayload: jsonDecode(map['local_payload'] as String) as Map<String, dynamic>,
              serverPayload: jsonDecode(map['server_payload'] as String) as Map<String, dynamic>,
              conflictingFields: (map['conflicting_fields'] as String? ?? '')
                  .split(',')
                  .where((s) => s.isNotEmpty)
                  .toList(),
              createdAt: DateTime.parse(map['created_at'] as String),
              status: map['status'] as String? ?? 'OPEN',
            );
          }
        }

        class SyncService extends ChangeNotifier {
          static final SyncService instance = SyncService._();
          SyncService._();

          final AppDatabase _db = AppDatabase.instance;
          final OutboxService _outbox = OutboxService.instance;
          final ApiClient _client = ApiClient.instance;
          static const Uuid _uuid = Uuid();

          bool _isOnline = false;
          bool _isSyncing = false;
          int _pendingCount = 0;
          int _conflictCount = 0;
          DateTime? _lastSyncTime;
          String? _lastError;

          bool get isOnline => _isOnline;
          bool get isSyncing => _isSyncing;
          int get pendingCount => _pendingCount;
          int get conflictCount => _conflictCount;
          DateTime? get lastSyncTime => _lastSyncTime;
          String? get lastError => _lastError;

          final Set<String> _registeredEntities = {};

          void registerEntity(String entity) {
            _registeredEntities.add(entity);
          }

          Future<void> init() async {
            await refreshCounts();
            await checkConnectivity();
          }

          Future<void> refreshCounts() async {
            final db = await _db.database;
            _pendingCount = await _outbox.getPendingCount();

            final conflictRows = await db.rawQuery(
              "SELECT COUNT(*) as count FROM conflict_records WHERE status = 'OPEN'",
            );
            _conflictCount = Sqflite.firstIntValue(conflictRows) ?? 0;
            notifyListeners();
          }

          Future<bool> checkConnectivity() async {
            try {
              final baseUrl = await AppConfig.getBaseUrl();
              final uri = Uri.parse(baseUrl);
              final socket = await Socket.connect(uri.host, uri.port, timeout: const Duration(seconds: 2));
              socket.destroy();
              _isOnline = true;
            } catch (_) {
              try {
                final baseUrl = await AppConfig.getBaseUrl();
                final res = await http.get(Uri.parse(baseUrl)).timeout(const Duration(seconds: 2));
                _isOnline = res.statusCode >= 200 && res.statusCode < 500;
              } catch (_) {
                _isOnline = false;
              }
            }
            notifyListeners();
            return _isOnline;
          }

          Future<void> syncAll() async {
            if (_isSyncing) return;
            _isSyncing = true;
            _lastError = null;
            notifyListeners();

            try {
              final online = await checkConnectivity();
              if (!online) {
                _isSyncing = false;
                notifyListeners();
                return;
              }

              await syncUp();
              await syncDown();

              _lastSyncTime = DateTime.now();
            } catch (e) {
              _lastError = e.toString();
            } finally {
              await refreshCounts();
              _isSyncing = false;
              notifyListeners();
            }
          }

          Future<void> syncUp() async {
            final ops = await _outbox.getPendingOperations();
            final Map<String, String> idMappings = {};

            for (final op in ops) {
              try {
                if (op.action == 'CREATE') {
                  await _processCreate(op, idMappings);
                } else if (op.action == 'UPDATE') {
                  await _processUpdate(op, idMappings);
                } else if (op.action == 'DELETE') {
                  await _processDelete(op, idMappings);
                }
              } catch (e) {
                await _outbox.markFailed(op.id, e.toString());
              }
            }
          }

          Future<void> _processCreate(OutboxOperation op, Map<String, String> idMappings) async {
            final payload = Map<String, dynamic>.from(op.payload ?? {});

            for (final entry in idMappings.entries) {
              payload.forEach((k, v) {
                if (v == entry.key) {
                  payload[k] = entry.value;
                }
              });
            }

            try {
              final res = await _client.post(
                '/api/${op.entity}',
                body: payload,
                headers: {'Idempotency-Key': op.id},
              );

              if (res is Map<String, dynamic>) {
                final serverId = res['id']?.toString() ?? op.recordId;
                if (serverId != op.recordId) {
                  idMappings[op.recordId] = serverId;
                  await _db.updateCachedEntityId(
                    op.entity,
                    op.recordId,
                    serverId,
                    res,
                    version: (res['version'] as int?) ?? 1,
                    syncStatus: 'synced',
                  );
                } else {
                  await _db.saveCachedEntity(
                    op.entity,
                    op.recordId,
                    res,
                    version: (res['version'] as int?) ?? 1,
                    syncStatus: 'synced',
                  );
                }
              }
              await _outbox.markCompleted(op.id);
            } catch (e) {
              if (e.toString().contains('409')) {
                await _outbox.markCompleted(op.id);
              } else {
                rethrow;
              }
            }
          }

          Future<void> _processUpdate(OutboxOperation op, Map<String, String> idMappings) async {
            final effectiveId = idMappings[op.recordId] ?? op.recordId;
            final localPayload = Map<String, dynamic>.from(op.payload ?? {});

            dynamic remote;
            try {
              remote = await _client.get('/api/${op.entity}/$effectiveId');
            } catch (e) {
              if (e.toString().contains('404')) {
                await _recordConflict(
                  op: op,
                  recordId: effectiveId,
                  serverVersion: 0,
                  localPayload: localPayload,
                  serverPayload: {'_deleted': true, 'message': 'Registro eliminado en servidor'},
                  conflictingFields: ['[ELIMINADO_EN_SERVIDOR]'],
                );
                return;
              }
              rethrow;
            }

            if (remote is! Map<String, dynamic>) return;
            final serverData = Map<String, dynamic>.from(remote);
            final serverVersion = (serverData['version'] as int?) ?? 0;
            final baseVersion = op.baseVersion;

            final List<String> conflictingFields = [];
            localPayload.forEach((key, localVal) {
              if (key.startsWith('_')) return;
              final serverVal = serverData[key];
              if (serverVal != null && serverVal != localVal) {
                conflictingFields.add(key);
              }
            });

            if (conflictingFields.isEmpty || serverVersion <= baseVersion) {
              final res = await _client.put(
                '/api/${op.entity}/$effectiveId',
                body: localPayload,
                headers: {'Idempotency-Key': op.id},
              );
              final finalData = res is Map<String, dynamic> ? res : localPayload;
              await _db.saveCachedEntity(
                op.entity,
                effectiveId,
                finalData,
                version: (finalData['version'] as int?) ?? (baseVersion + 1),
                syncStatus: 'synced',
              );
              await _outbox.markCompleted(op.id);
            } else {
              await _recordConflict(
                op: op,
                recordId: effectiveId,
                serverVersion: serverVersion,
                localPayload: localPayload,
                serverPayload: serverData,
                conflictingFields: conflictingFields,
              );
            }
          }

          Future<void> _processDelete(OutboxOperation op, Map<String, String> idMappings) async {
            final effectiveId = idMappings[op.recordId] ?? op.recordId;
            try {
              await _client.delete('/api/${op.entity}/$effectiveId');
              await _db.removeCachedEntity(op.entity, effectiveId);
              await _outbox.markCompleted(op.id);
            } catch (e) {
              if (e.toString().contains('404')) {
                await _db.removeCachedEntity(op.entity, effectiveId);
                await _outbox.markCompleted(op.id);
              } else {
                rethrow;
              }
            }
          }

          Future<void> _recordConflict({
            required OutboxOperation op,
            required String recordId,
            required int serverVersion,
            required Map<String, dynamic> localPayload,
            required Map<String, dynamic> serverPayload,
            required List<String> conflictingFields,
          }) async {
            final db = await _db.database;
            final conflictId = _uuid.v4();
            final conflict = ConflictRecord(
              id: conflictId,
              operationId: op.id,
              entity: op.entity,
              recordId: recordId,
              baseVersion: op.baseVersion,
              serverVersion: serverVersion,
              localPayload: localPayload,
              serverPayload: serverPayload,
              conflictingFields: conflictingFields,
              createdAt: DateTime.now(),
              status: 'OPEN',
            );

            await db.transaction((txn) async {
              await txn.insert('conflict_records', conflict.toMap());
              await txn.update(
                'outbox_operations',
                {'status': 'CONFLICT', 'error_message': 'Conflicto de concurrencia detectado'},
                where: 'id = ?',
                whereArgs: [op.id],
              );
              await txn.update(
                'cached_entities',
                {'sync_status': 'conflict'},
                where: 'entity_type = ? AND id = ?',
                whereArgs: [op.entity, recordId],
              );
            });

            await refreshCounts();
          }

          Future<void> syncDown() async {
            for (final entity in _registeredEntities) {
              try {
                final res = await _client.get('/api/$entity');
                if (res is List) {
                  for (final item in res) {
                    if (item is Map<String, dynamic>) {
                      final id = item['id']?.toString();
                      if (id != null) {
                        final local = await _db.getCachedEntity(entity, id);
                        if (local == null || local['_syncStatus'] == 'synced') {
                          await _db.saveCachedEntity(
                            entity,
                            id,
                            item,
                            version: (item['version'] as int?) ?? 1,
                            syncStatus: 'synced',
                          );
                        }
                      }
                    }
                  }
                }
              } catch (_) {}
            }
          }

          Future<List<ConflictRecord>> getOpenConflicts() async {
            final db = await _db.database;
            final rows = await db.query(
              'conflict_records',
              where: 'status = ?',
              whereArgs: ['OPEN'],
              orderBy: 'created_at DESC',
            );
            return rows.map((r) => ConflictRecord.fromMap(r)).toList();
          }

          Future<void> resolveConflictKeepLocal(String conflictId) async {
            final db = await _db.database;
            final rows = await db.query(
              'conflict_records',
              where: 'id = ?',
              whereArgs: [conflictId],
              limit: 1,
            );
            if (rows.isEmpty) return;
            final conflict = ConflictRecord.fromMap(rows.first);

            await _client.put(
              '/api/${conflict.entity}/${conflict.recordId}',
              body: conflict.localPayload,
            );

            await db.transaction((txn) async {
              await txn.update(
                'conflict_records',
                {'status': 'RESOLVED_LOCAL'},
                where: 'id = ?',
                whereArgs: [conflictId],
              );
              await txn.delete(
                'outbox_operations',
                where: 'id = ?',
                whereArgs: [conflict.operationId],
              );
              await txn.update(
                'cached_entities',
                {
                  'data': jsonEncode(conflict.localPayload),
                  'sync_status': 'synced',
                },
                where: 'entity_type = ? AND id = ?',
                whereArgs: [conflict.entity, conflict.recordId],
              );
            });

            await refreshCounts();
          }

          Future<void> resolveConflictDiscardLocal(String conflictId) async {
            final db = await _db.database;
            final rows = await db.query(
              'conflict_records',
              where: 'id = ?',
              whereArgs: [conflictId],
              limit: 1,
            );
            if (rows.isEmpty) return;
            final conflict = ConflictRecord.fromMap(rows.first);

            await db.transaction((txn) async {
              await txn.update(
                'conflict_records',
                {'status': 'RESOLVED_SERVER'},
                where: 'id = ?',
                whereArgs: [conflictId],
              );
              await txn.delete(
                'outbox_operations',
                where: 'id = ?',
                whereArgs: [conflict.operationId],
              );

              if (conflict.serverPayload['_deleted'] == true) {
                await txn.delete(
                  'cached_entities',
                  where: 'entity_type = ? AND id = ?',
                  whereArgs: [conflict.entity, conflict.recordId],
                );
              } else {
                await txn.update(
                  'cached_entities',
                  {
                    'data': jsonEncode(conflict.serverPayload),
                    'version': conflict.serverVersion,
                    'sync_status': 'synced',
                  },
                  where: 'entity_type = ? AND id = ?',
                  whereArgs: [conflict.entity, conflict.recordId],
                );
              }
            });

            await refreshCounts();
          }

          Future<void> resolveConflictManual(String conflictId, Map<String, dynamic> mergedPayload) async {
            final db = await _db.database;
            final rows = await db.query(
              'conflict_records',
              where: 'id = ?',
              whereArgs: [conflictId],
              limit: 1,
            );
            if (rows.isEmpty) return;
            final conflict = ConflictRecord.fromMap(rows.first);

            await _client.put(
              '/api/${conflict.entity}/${conflict.recordId}',
              body: mergedPayload,
            );

            await db.transaction((txn) async {
              await txn.update(
                'conflict_records',
                {'status': 'RESOLVED_EDIT'},
                where: 'id = ?',
                whereArgs: [conflictId],
              );
              await txn.delete(
                'outbox_operations',
                where: 'id = ?',
                whereArgs: [conflict.operationId],
              );
              await txn.update(
                'cached_entities',
                {
                  'data': jsonEncode(mergedPayload),
                  'sync_status': 'synced',
                },
                where: 'entity_type = ? AND id = ?',
                whereArgs: [conflict.entity, conflict.recordId],
              );
            });

            await refreshCounts();
          }
        }
        """.formatted(pubName, pubName, pubName, pubName);
    }

    private String syncStatusBadge(String pubName) {
        return """
        import 'package:flutter/material.dart';
        import 'package:%s/core/sync/sync_service.dart';
        import 'package:%s/presentation/screens/conflicts/conflict_resolution_screen.dart';

        class SyncStatusBadge extends StatelessWidget {
          const SyncStatusBadge({super.key});

          @override
          Widget build(BuildContext context) {
            return AnimatedBuilder(
              animation: SyncService.instance,
              builder: (context, _) {
                final sync = SyncService.instance;
                return Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Tooltip(
                      message: sync.isOnline ? 'Conectado al servidor' : 'Modo sin conexión (Offline)',
                      child: InkWell(
                        onTap: () => sync.syncAll(),
                        borderRadius: BorderRadius.circular(12),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Container(
                                width: 9,
                                height: 9,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: sync.isOnline ? Colors.green : Colors.grey,
                                ),
                              ),
                              const SizedBox(width: 4),
                              Text(
                                sync.isOnline ? 'Online' : 'Offline',
                                style: TextStyle(
                                  fontSize: 12,
                                  color: sync.isOnline ? Colors.white : Colors.white70,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                    if (sync.pendingCount > 0) ...[
                      const SizedBox(width: 4),
                      Tooltip(
                        message: '${sync.pendingCount} operaciones pendientes en outbox',
                        child: Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: Colors.orange.shade800,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.cloud_upload, size: 12, color: Colors.white),
                              const SizedBox(width: 3),
                              Text(
                                '${sync.pendingCount}',
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontSize: 11,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                    if (sync.conflictCount > 0) ...[
                      const SizedBox(width: 4),
                      Tooltip(
                        message: '${sync.conflictCount} conflicto(s) por resolver',
                        child: InkWell(
                          onTap: () {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => const ConflictResolutionScreen(),
                              ),
                            );
                          },
                          borderRadius: BorderRadius.circular(10),
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: Colors.red.shade700,
                              borderRadius: BorderRadius.circular(10),
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(Icons.warning_amber_rounded, size: 13, color: Colors.white),
                                const SizedBox(width: 2),
                                Text(
                                  '${sync.conflictCount}',
                                  style: const TextStyle(
                                    color: Colors.white,
                                    fontSize: 11,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    ],
                    IconButton(
                      icon: sync.isSyncing
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                              ),
                            )
                          : const Icon(Icons.sync, size: 20),
                      tooltip: 'Sincronizar ahora',
                      onPressed: sync.isSyncing ? null : () => sync.syncAll(),
                    ),
                  ],
                );
              },
            );
          }
        }
        """.formatted(pubName, pubName);
    }

    private String conflictResolutionScreen(String pubName) {
        return """
        import 'package:flutter/material.dart';
        import 'package:%s/core/sync/sync_service.dart';

        class ConflictResolutionScreen extends StatefulWidget {
          const ConflictResolutionScreen({super.key});

          @override
          State<ConflictResolutionScreen> createState() => _ConflictResolutionScreenState();
        }

        class _ConflictResolutionScreenState extends State<ConflictResolutionScreen> {
          final SyncService _syncService = SyncService.instance;
          List<ConflictRecord> _conflicts = [];
          bool _isLoading = true;

          @override
          void initState() {
            super.initState();
            _loadConflicts();
          }

          Future<void> _loadConflicts() async {
            setState(() => _isLoading = true);
            final list = await _syncService.getOpenConflicts();
            setState(() {
              _conflicts = list;
              _isLoading = false;
            });
          }

          @override
          Widget build(BuildContext context) {
            return Scaffold(
              appBar: AppBar(
                title: const Text('Resolución de Conflictos'),
                actions: [
                  IconButton(
                    icon: const Icon(Icons.refresh),
                    onPressed: _loadConflicts,
                  ),
                ],
              ),
              body: _isLoading
                  ? const Center(child: CircularProgressIndicator())
                  : _conflicts.isEmpty
                      ? _buildEmptyState()
                      : ListView.builder(
                          padding: const EdgeInsets.all(16),
                          itemCount: _conflicts.length,
                          itemBuilder: (context, index) {
                            final conflict = _conflicts[index];
                            return _buildConflictCard(conflict);
                          },
                        ),
            );
          }

          Widget _buildEmptyState() {
            return Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.check_circle_outline, size: 64, color: Colors.green.shade600),
                  const SizedBox(height: 16),
                  const Text(
                    '¡No hay conflictos pendientes!',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    'Todos los cambios locales y remotos están sincronizados.',
                    style: TextStyle(color: Colors.grey),
                  ),
                ],
              ),
            );
          }

          Widget _buildConflictCard(ConflictRecord conflict) {
            final allKeys = <String>{
              ...conflict.localPayload.keys,
              ...conflict.serverPayload.keys,
            }..removeWhere((k) => k.startsWith('_'));

            return Card(
              margin: const EdgeInsets.only(bottom: 16),
              elevation: 3,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
                side: BorderSide(color: Colors.red.shade300, width: 1.5),
              ),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                          decoration: BoxDecoration(
                            color: Colors.red.shade100,
                            borderRadius: BorderRadius.circular(6),
                          ),
                          child: Text(
                            conflict.entity.toUpperCase(),
                            style: TextStyle(
                              color: Colors.red.shade900,
                              fontWeight: FontWeight.bold,
                              fontSize: 12,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            'Registro: ${conflict.recordId}',
                            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Campos en conflicto: ${conflict.conflictingFields.join(', ')}',
                      style: TextStyle(color: Colors.red.shade700, fontWeight: FontWeight.w600, fontSize: 13),
                    ),
                    const Divider(height: 24),
                    Table(
                      border: TableBorder.all(color: Colors.grey.shade300, borderRadius: BorderRadius.circular(4)),
                      columnWidths: const {
                        0: FlexColumnWidth(1.2),
                        1: FlexColumnWidth(2),
                        2: FlexColumnWidth(2),
                      },
                      children: [
                        TableRow(
                          decoration: BoxDecoration(color: Colors.grey.shade100),
                          children: const [
                            Padding(
                              padding: EdgeInsets.all(8),
                              child: Text('Campo', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                            ),
                            Padding(
                              padding: EdgeInsets.all(8),
                              child: Text('Valor Local (Móvil)', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.blue, fontSize: 12)),
                            ),
                            Padding(
                              padding: EdgeInsets.all(8),
                              child: Text('Valor Servidor (Remoto)', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.purple, fontSize: 12)),
                            ),
                          ],
                        ),
                        for (final key in allKeys)
                          _buildComparisonRow(
                            field: key,
                            localVal: conflict.localPayload[key]?.toString() ?? '—',
                            serverVal: conflict.serverPayload[key]?.toString() ?? '—',
                            isConflict: conflict.conflictingFields.contains(key),
                          ),
                      ],
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(
                          child: ElevatedButton.icon(
                            icon: const Icon(Icons.phone_android, size: 16),
                            label: const Text('Conservar local'),
                            style: ElevatedButton.styleFrom(
                              backgroundColor: Colors.blue.shade700,
                              foregroundColor: Colors.white,
                            ),
                            onPressed: () async {
                              await _syncService.resolveConflictKeepLocal(conflict.id);
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('Se conservó el valor local en el servidor.')),
                              );
                              await _loadConflicts();
                            },
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: OutlinedButton.icon(
                            icon: const Icon(Icons.cloud_download, size: 16),
                            label: const Text('Descartar local'),
                            style: OutlinedButton.styleFrom(
                              foregroundColor: Colors.purple.shade700,
                            ),
                            onPressed: () async {
                              await _syncService.resolveConflictDiscardLocal(conflict.id);
                              ScaffoldMessenger.of(context).showSnackBar(
                                const SnackBar(content: Text('Se descartó el cambio local y se adoptó el servidor.')),
                              );
                              await _loadConflicts();
                            },
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    SizedBox(
                      width: double.infinity,
                      child: TextButton.icon(
                        icon: const Icon(Icons.edit, size: 16),
                        label: const Text('Editar de nuevo y resolver manualmente'),
                        onPressed: () => _showManualEditDialog(conflict),
                      ),
                    ),
                  ],
                ),
              ),
            );
          }

          TableRow _buildComparisonRow({
            required String field,
            required String localVal,
            required String serverVal,
            required bool isConflict,
          }) {
            return TableRow(
              decoration: BoxDecoration(
                color: isConflict ? Colors.red.shade50 : null,
              ),
              children: [
                Padding(
                  padding: const EdgeInsets.all(8),
                  child: Text(
                    field,
                    style: TextStyle(
                      fontWeight: isConflict ? FontWeight.bold : FontWeight.normal,
                      color: isConflict ? Colors.red.shade900 : Colors.black87,
                      fontSize: 12,
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.all(8),
                  child: Text(
                    localVal,
                    style: TextStyle(
                      fontWeight: isConflict ? FontWeight.bold : FontWeight.normal,
                      color: isConflict ? Colors.blue.shade900 : Colors.black87,
                      fontSize: 12,
                    ),
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.all(8),
                  child: Text(
                    serverVal,
                    style: TextStyle(
                      fontWeight: isConflict ? FontWeight.bold : FontWeight.normal,
                      color: isConflict ? Colors.purple.shade900 : Colors.black87,
                      fontSize: 12,
                    ),
                  ),
                ),
              ],
            );
          }

          void _showManualEditDialog(ConflictRecord conflict) {
            final Map<String, TextEditingController> controllers = {};
            final allKeys = <String>{
              ...conflict.localPayload.keys,
              ...conflict.serverPayload.keys,
            }..removeWhere((k) => k.startsWith('_') || k == 'id');

            for (final key in allKeys) {
              controllers[key] = TextEditingController(
                text: conflict.localPayload[key]?.toString() ??
                    conflict.serverPayload[key]?.toString() ??
                    '',
              );
            }

            showDialog(
              context: context,
              builder: (dialogCtx) {
                return AlertDialog(
                  title: Text('Editar y fusionar ${conflict.entity}'),
                  content: SingleChildScrollView(
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        for (final key in allKeys)
                          Padding(
                            padding: const EdgeInsets.only(bottom: 12),
                            child: TextField(
                              controller: controllers[key],
                              decoration: InputDecoration(
                                labelText: key,
                                helperText: 'Servidor: ${conflict.serverPayload[key]} | Local: ${conflict.localPayload[key]}',
                                border: const OutlineInputBorder(),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(dialogCtx),
                      child: const Text('Cancelar'),
                    ),
                    ElevatedButton(
                      onPressed: () async {
                        final merged = <String, dynamic>{};
                        for (final entry in controllers.entries) {
                          merged[entry.key] = entry.value.text;
                        }
                        Navigator.pop(dialogCtx);
                        await _syncService.resolveConflictManual(conflict.id, merged);
                        ScaffoldMessenger.of(context).showSnackBar(
                          const SnackBar(content: Text('Conflicto resuelto con los valores editados.')),
                        );
                        await _loadConflicts();
                      },
                      child: const Text('Guardar y sincronizar'),
                    ),
                  ],
                );
              },
            );
          }
        }
        """.formatted(pubName);
    }

    private String appTheme() {
        return """
        import 'package:flutter/material.dart';

        class AppTheme {
          static ThemeData get lightTheme {
            final colorScheme = ColorScheme.fromSeed(
              seedColor: const Color(0xFF1E3A8A),
              brightness: Brightness.light,
              primary: const Color(0xFF1E3A8A),
              secondary: const Color(0xFF0D9488),
              surface: const Color(0xFFF8FAFC),
              error: const Color(0xFFDC2626),
            );

            return ThemeData(
              useMaterial3: true,
              colorScheme: colorScheme,
              scaffoldBackgroundColor: const Color(0xFFF1F5F9),
              appBarTheme: AppBarTheme(
                backgroundColor: colorScheme.primary,
                foregroundColor: Colors.white,
                elevation: 0,
                centerTitle: false,
                titleTextStyle: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w600,
                  color: Colors.white,
                ),
              ),
              cardTheme: CardThemeData(
                elevation: 1,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                  side: BorderSide(color: Colors.grey.shade300, width: 0.8),
                ),
                color: Colors.white,
                margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              ),
              inputDecorationTheme: InputDecorationTheme(
                filled: true,
                fillColor: Colors.white,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: BorderSide(color: Colors.grey.shade400),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: BorderSide(color: Colors.grey.shade400),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: BorderSide(color: colorScheme.primary, width: 2),
                ),
                errorBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(8),
                  borderSide: BorderSide(color: colorScheme.error),
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
              ),
              elevatedButtonTheme: ElevatedButtonThemeData(
                style: ElevatedButton.styleFrom(
                  backgroundColor: colorScheme.primary,
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
                ),
              ),
              outlinedButtonTheme: OutlinedButtonThemeData(
                style: OutlinedButton.styleFrom(
                  foregroundColor: colorScheme.primary,
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                ),
              ),
            );
          }
        }
        """;
    }

    private String appStrings(DiagramDocument diagram) {
        return """
        class AppStrings {
          static const String appName = '%s';
          static const String login = 'Iniciar sesión';
          static const String register = 'Registrarse';
          static const String logout = 'Cerrar sesión';
          static const String email = 'Correo electrónico';
          static const String password = 'Contraseña';
          static const String fullName = 'Nombre completo';
          static const String save = 'Guardar';
          static const String cancel = 'Cancelar';
          static const String delete = 'Eliminar';
          static const String edit = 'Editar';
          static const String search = 'Buscar...';
          static const String details = 'Detalles';
          static const String create = 'Crear';
          static const String retry = 'Reintentar';
          static const String empty = 'No se encontraron registros';
          static const String loading = 'Cargando datos...';
          static const String serverSettings = 'Configuración del servidor';
          static const String serverUrl = 'URL de la API';
          static const String testConnection = 'Probar conexión';
          static const String connectionSuccess = 'Conexión exitosa con el backend';
          static const String connectionFailed = 'No se pudo conectar con el servidor';
          static const String confirmDeleteTitle = '¿Eliminar registro?';
          static const String confirmDeleteMessage = 'Esta acción no se puede deshacer. ¿Deseas continuar?';
          static const String fieldRequired = 'Este campo es obligatorio';
          static const String invalidNumber = 'Ingresa un número válido';
          static const String savedSuccess = 'Guardado exitosamente';
          static const String deletedSuccess = 'Eliminado exitosamente';
        }
        """.formatted(diagram.name());
    }

    private String asyncStateView(String pubName) {
        return """
        import 'package:flutter/material.dart';
        import 'package:%s/core/i18n/app_strings.dart';

        class AsyncStateView extends StatelessWidget {
          final bool isLoading;
          final String? errorMessage;
          final bool isEmpty;
          final VoidCallback? onRetry;
          final String emptyMessage;
          final Widget child;

          const AsyncStateView({
            super.key,
            required this.isLoading,
            this.errorMessage,
            required this.isEmpty,
            this.onRetry,
            this.emptyMessage = AppStrings.empty,
            required this.child,
          });

          @override
          Widget build(BuildContext context) {
            if (isLoading) {
              return const Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    CircularProgressIndicator(),
                    SizedBox(height: 16),
                    Text(AppStrings.loading, style: TextStyle(color: Colors.grey)),
                  ],
                ),
              );
            }

            if (errorMessage != null && errorMessage!.isNotEmpty) {
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(24.0),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.error_outline, size: 54, color: Colors.red),
                      const SizedBox(height: 12),
                      Text(
                        errorMessage!,
                        textAlign: TextAlign.center,
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
                      ),
                      if (onRetry != null) ...[
                        const SizedBox(height: 16),
                        ElevatedButton.icon(
                          onPressed: onRetry,
                          icon: const Icon(Icons.refresh),
                          label: const Text(AppStrings.retry),
                        ),
                      ],
                    ],
                  ),
                ),
              );
            }

            if (isEmpty) {
              return Center(
                child: Padding(
                  padding: const EdgeInsets.all(24.0),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(Icons.inbox_outlined, size: 54, color: Colors.grey.shade400),
                      const SizedBox(height: 12),
                      Text(
                        emptyMessage,
                        style: TextStyle(fontSize: 16, color: Colors.grey.shade600),
                      ),
                      if (onRetry != null) ...[
                        const SizedBox(height: 16),
                        TextButton.icon(
                          onPressed: onRetry,
                          icon: const Icon(Icons.refresh),
                          label: const Text(AppStrings.retry),
                        ),
                      ],
                    ],
                  ),
                ),
              );
            }

            return child;
          }
        }
        """.formatted(pubName);
    }

    private String confirmDialog(String pubName) {
        return """
        import 'package:flutter/material.dart';
        import 'package:%s/core/i18n/app_strings.dart';

        class ConfirmDialog {
          static Future<bool> show(
            BuildContext context, {
            String title = AppStrings.confirmDeleteTitle,
            String message = AppStrings.confirmDeleteMessage,
            String confirmText = AppStrings.delete,
            String cancelText = AppStrings.cancel,
          }) async {
            final result = await showDialog<bool>(
              context: context,
              builder: (ctx) => AlertDialog(
                title: Text(title),
                content: Text(message),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.of(ctx).pop(false),
                    child: Text(cancelText),
                  ),
                  ElevatedButton(
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
                    onPressed: () => Navigator.of(ctx).pop(true),
                    child: Text(confirmText),
                  ),
                ],
              ),
            );
            return result ?? false;
          }
        }
        """.formatted(pubName);
    }

    private String relationPicker() {
        return """
        import 'package:flutter/material.dart';

        class RelationDropdown<T> extends StatelessWidget {
          final String label;
          final T? value;
          final List<T> items;
          final String Function(T) itemLabel;
          final dynamic Function(T) itemValue;
          final ValueChanged<T?> onChanged;
          final bool isRequired;

          const RelationDropdown({
            super.key,
            required this.label,
            required this.value,
            required this.items,
            required this.itemLabel,
            required this.itemValue,
            required this.onChanged,
            this.isRequired = false,
          });

          @override
          Widget build(BuildContext context) {
            return DropdownButtonFormField<T>(
              initialValue: value,
              decoration: InputDecoration(
                labelText: isRequired ? '$label *' : label,
              ),
              isExpanded: true,
              items: [
                if (!isRequired)
                  DropdownMenuItem<T>(
                    value: null,
                    child: const Text('Ninguno', style: TextStyle(color: Colors.grey)),
                  ),
                ...items.map((item) => DropdownMenuItem<T>(
                      value: item,
                      child: Text(itemLabel(item), overflow: TextOverflow.ellipsis),
                    )),
              ],
              onChanged: onChanged,
              validator: isRequired
                  ? (val) => val == null ? 'Selecciona una opción' : null
                  : null,
            );
          }
        }

        class RelationMultiSelectDialog<T> extends StatefulWidget {
          final String title;
          final List<T> allItems;
          final List<T> selectedItems;
          final String Function(T) itemLabel;

          const RelationMultiSelectDialog({
            super.key,
            required this.title,
            required this.allItems,
            required this.selectedItems,
            required this.itemLabel,
          });

          @override
          State<RelationMultiSelectDialog<T>> createState() => _RelationMultiSelectDialogState<T>();
        }

        class _RelationMultiSelectDialogState<T> extends State<RelationMultiSelectDialog<T>> {
          late List<T> _tempSelected;

          @override
          void initState() {
            super.initState();
            _tempSelected = List<T>.from(widget.selectedItems);
          }

          @override
          Widget build(BuildContext context) {
            return AlertDialog(
              title: Text(widget.title),
              content: SizedBox(
                width: double.maxFinite,
                child: widget.allItems.isEmpty
                    ? const Padding(
                        padding: EdgeInsets.all(16.0),
                        child: Text('No hay elementos disponibles.'),
                      )
                    : ListView.builder(
                        shrinkWrap: true,
                        itemCount: widget.allItems.length,
                        itemBuilder: (ctx, i) {
                          final item = widget.allItems[i];
                          final isChecked = _tempSelected.contains(item);
                          return CheckboxListTile(
                            title: Text(widget.itemLabel(item)),
                            value: isChecked,
                            onChanged: (val) {
                              setState(() {
                                if (val == true) {
                                  _tempSelected.add(item);
                                } else {
                                  _tempSelected.remove(item);
                                }
                              });
                            },
                          );
                        },
                      ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Cancelar'),
                ),
                ElevatedButton(
                  onPressed: () => Navigator.of(context).pop(_tempSelected),
                  child: const Text('Aceptar'),
                ),
              ],
            );
          }
        }
        """;
    }

    // =========================================================================
    // 3. AUTH & REPOSITORY TEMPLATES
    // =========================================================================

    private String authModels() {
        return """
        class UserProfile {
          final String id;
          final String email;
          final String fullName;
          final String role;

          UserProfile({
            required this.id,
            required this.email,
            required this.fullName,
            required this.role,
          });

          factory UserProfile.fromJson(Map<String, dynamic> json) {
            return UserProfile(
              id: json['id']?.toString() ?? '',
              email: json['email']?.toString() ?? '',
              fullName: json['fullName']?.toString() ?? '',
              role: json['role']?.toString() ?? 'ROLE_USER',
            );
          }

          Map<String, dynamic> toJson() => {
            'id': id,
            'email': email,
            'fullName': fullName,
            'role': role,
          };
        }

        class AuthResponse {
          final String accessToken;
          final String refreshToken;
          final String tokenType;
          final UserProfile user;

          AuthResponse({
            required this.accessToken,
            required this.refreshToken,
            this.tokenType = 'Bearer',
            required this.user,
          });

          factory AuthResponse.fromJson(Map<String, dynamic> json) {
            return AuthResponse(
              accessToken: json['accessToken']?.toString() ?? '',
              refreshToken: json['refreshToken']?.toString() ?? '',
              tokenType: json['tokenType']?.toString() ?? 'Bearer',
              user: UserProfile.fromJson(json['user'] ?? {}),
            );
          }
        }
        """;
    }

    private String authRepository(String pubName) {
        return """
        import 'dart:convert';
        import 'package:shared_preferences/shared_preferences.dart';
        import 'package:%s/core/api/api_client.dart';
        import 'package:%s/data/models/auth_models.dart';

        class AuthRepository {
          final ApiClient _client = ApiClient.instance;

          Future<AuthResponse> login(String email, String password) async {
            final data = await _client.post('/api/auth/login', body: {
              'email': email,
              'password': password,
            });
            final response = AuthResponse.fromJson(data);
            await _client.saveTokens(
              accessToken: response.accessToken,
              refreshToken: response.refreshToken,
            );
            await _saveUser(response.user);
            return response;
          }

          Future<AuthResponse> register(String fullName, String email, String password) async {
            final data = await _client.post('/api/auth/register', body: {
              'fullName': fullName,
              'email': email,
              'password': password,
            });
            final response = AuthResponse.fromJson(data);
            await _client.saveTokens(
              accessToken: response.accessToken,
              refreshToken: response.refreshToken,
            );
            await _saveUser(response.user);
            return response;
          }

          Future<UserProfile> me() async {
            final data = await _client.get('/api/auth/me');
            final user = UserProfile.fromJson(data);
            await _saveUser(user);
            return user;
          }

          Future<void> logout() async {
            await _client.clearTokens();
            final prefs = await SharedPreferences.getInstance();
            await prefs.remove('auth_user_profile');
          }

          Future<UserProfile?> getCachedUser() async {
            final prefs = await SharedPreferences.getInstance();
            final str = prefs.getString('auth_user_profile');
            if (str != null) {
              try {
                return UserProfile.fromJson(jsonDecode(str));
              } catch (_) {}
            }
            return null;
          }

          Future<void> _saveUser(UserProfile user) async {
            final prefs = await SharedPreferences.getInstance();
            await prefs.setString('auth_user_profile', jsonEncode(user.toJson()));
          }
        }
        """.formatted(pubName, pubName);
    }

    private String authProvider(String pubName) {
        return """
        import 'package:flutter/material.dart';
        import 'package:%s/core/api/api_client.dart';
        import 'package:%s/data/models/auth_models.dart';
        import 'package:%s/data/repositories/auth_repository.dart';

        class AuthProvider extends ChangeNotifier {
          final AuthRepository _repository = AuthRepository();

          UserProfile? _user;
          bool _isLoading = false;
          String? _errorMessage;

          UserProfile? get user => _user;
          bool get isLoading => _isLoading;
          String? get errorMessage => _errorMessage;
          bool get isAuthenticated => ApiClient.instance.isAuthenticated;

          Future<void> init() async {
            await ApiClient.instance.init();
            if (isAuthenticated) {
              _user = await _repository.getCachedUser();
              notifyListeners();
              try {
                _user = await _repository.me();
                notifyListeners();
              } catch (_) {}
            }
          }

          Future<bool> login(String email, String password) async {
            _setLoading(true);
            try {
              final res = await _repository.login(email, password);
              _user = res.user;
              _errorMessage = null;
              _setLoading(false);
              return true;
            } catch (e) {
              _errorMessage = e.toString();
              _setLoading(false);
              return false;
            }
          }

          Future<bool> register(String fullName, String email, String password) async {
            _setLoading(true);
            try {
              final res = await _repository.register(fullName, email, password);
              _user = res.user;
              _errorMessage = null;
              _setLoading(false);
              return true;
            } catch (e) {
              _errorMessage = e.toString();
              _setLoading(false);
              return false;
            }
          }

          Future<void> logout() async {
            await _repository.logout();
            _user = null;
            notifyListeners();
          }

          void _setLoading(bool value) {
            _isLoading = value;
            notifyListeners();
          }
        }
        """.formatted(pubName, pubName, pubName);
    }

    // =========================================================================
    // 4. SCREEN TEMPLATES (Auth & Settings)
    // =========================================================================

    private String loginScreen(String pubName) {
        return """
        import 'package:flutter/material.dart';
        import 'package:%s/core/i18n/app_strings.dart';
        import 'package:%s/presentation/state/auth_provider.dart';
        import 'package:%s/presentation/screens/auth/register_screen.dart';
        import 'package:%s/presentation/screens/settings/server_settings_screen.dart';
        import 'package:%s/presentation/screens/home/home_screen.dart';

        class LoginScreen extends StatefulWidget {
          final AuthProvider authProvider;
          const LoginScreen({super.key, required this.authProvider});

          @override
          State<LoginScreen> createState() => _LoginScreenState();
        }

        class _LoginScreenState extends State<LoginScreen> {
          final _formKey = GlobalKey<FormState>();
          final _emailController = TextEditingController(text: 'admin@ventas.com');
          final _passwordController = TextEditingController(text: 'adminPass123');
          bool _obscure = true;

          @override
          void dispose() {
            _emailController.dispose();
            _passwordController.dispose();
            super.dispose();
          }

          Future<void> _submit() async {
            if (!_formKey.currentState!.validate()) return;
            final success = await widget.authProvider.login(
              _emailController.text.trim(),
              _passwordController.text,
            );
            if (success && mounted) {
              Navigator.of(context).pushReplacement(
                MaterialPageRoute(builder: (_) => HomeScreen(authProvider: widget.authProvider)),
              );
            } else if (mounted && widget.authProvider.errorMessage != null) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text(widget.authProvider.errorMessage!), backgroundColor: Colors.red),
              );
            }
          }

          @override
          Widget build(BuildContext context) {
            return Scaffold(
              appBar: AppBar(
                title: const Text(AppStrings.appName),
                actions: [
                  IconButton(
                    icon: const Icon(Icons.settings),
                    tooltip: AppStrings.serverSettings,
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute(builder: (_) => const ServerSettingsScreen()),
                    ),
                  ),
                ],
              ),
              body: Center(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(24.0),
                  child: Card(
                    child: Padding(
                      padding: const EdgeInsets.all(24.0),
                      child: Form(
                        key: _formKey,
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            const Icon(Icons.lock_person, size: 54, color: Color(0xFF1E3A8A)),
                            const SizedBox(height: 12),
                            const Text(
                              AppStrings.login,
                              textAlign: TextAlign.center,
                              style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
                            ),
                            const SizedBox(height: 20),
                            TextFormField(
                              controller: _emailController,
                              keyboardType: TextInputType.emailAddress,
                              decoration: const InputDecoration(
                                labelText: AppStrings.email,
                                prefixIcon: Icon(Icons.email_outlined),
                              ),
                              validator: (v) => (v == null || v.trim().isEmpty) ? AppStrings.fieldRequired : null,
                            ),
                            const SizedBox(height: 16),
                            TextFormField(
                              controller: _passwordController,
                              obscureText: _obscure,
                              decoration: InputDecoration(
                                labelText: AppStrings.password,
                                prefixIcon: const Icon(Icons.lock_outline),
                                suffixIcon: IconButton(
                                  icon: Icon(_obscure ? Icons.visibility_off : Icons.visibility),
                                  onPressed: () => setState(() => _obscure = !_obscure),
                                ),
                              ),
                              validator: (v) => (v == null || v.isEmpty) ? AppStrings.fieldRequired : null,
                            ),
                            const SizedBox(height: 24),
                            ListenableBuilder(
                              listenable: widget.authProvider,
                              builder: (ctx, _) {
                                return ElevatedButton(
                                  onPressed: widget.authProvider.isLoading ? null : _submit,
                                  child: widget.authProvider.isLoading
                                      ? const SizedBox(
                                          height: 20,
                                          width: 20,
                                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                        )
                                      : const Text(AppStrings.login),
                                );
                              },
                            ),
                            const SizedBox(height: 16),
                            TextButton(
                              onPressed: () => Navigator.of(context).push(
                                MaterialPageRoute(
                                  builder: (_) => RegisterScreen(authProvider: widget.authProvider),
                                ),
                              ),
                              child: const Text('¿No tienes cuenta? Regístrate aquí'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            );
          }
        }
        """.formatted(pubName, pubName, pubName, pubName, pubName);
    }

    private String registerScreen(String pubName) {
        return """
        import 'package:flutter/material.dart';
        import 'package:%s/core/i18n/app_strings.dart';
        import 'package:%s/presentation/state/auth_provider.dart';
        import 'package:%s/presentation/screens/home/home_screen.dart';

        class RegisterScreen extends StatefulWidget {
          final AuthProvider authProvider;
          const RegisterScreen({super.key, required this.authProvider});

          @override
          State<RegisterScreen> createState() => _RegisterScreenState();
        }

        class _RegisterScreenState extends State<RegisterScreen> {
          final _formKey = GlobalKey<FormState>();
          final _nameController = TextEditingController();
          final _emailController = TextEditingController();
          final _passwordController = TextEditingController();

          @override
          void dispose() {
            _nameController.dispose();
            _emailController.dispose();
            _passwordController.dispose();
            super.dispose();
          }

          Future<void> _submit() async {
            if (!_formKey.currentState!.validate()) return;
            final success = await widget.authProvider.register(
              _nameController.text.trim(),
              _emailController.text.trim(),
              _passwordController.text,
            );
            if (success && mounted) {
              Navigator.of(context).pushAndRemoveUntil(
                MaterialPageRoute(builder: (_) => HomeScreen(authProvider: widget.authProvider)),
                (route) => false,
              );
            } else if (mounted && widget.authProvider.errorMessage != null) {
              ScaffoldMessenger.of(context).showSnackBar(
                SnackBar(content: Text(widget.authProvider.errorMessage!), backgroundColor: Colors.red),
              );
            }
          }

          @override
          Widget build(BuildContext context) {
            return Scaffold(
              appBar: AppBar(title: const Text(AppStrings.register)),
              body: Center(
                child: SingleChildScrollView(
                  padding: const EdgeInsets.all(24.0),
                  child: Card(
                    child: Padding(
                      padding: const EdgeInsets.all(24.0),
                      child: Form(
                        key: _formKey,
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            const Icon(Icons.person_add_alt_1, size: 54, color: Color(0xFF1E3A8A)),
                            const SizedBox(height: 12),
                            const Text(
                              'Crear nueva cuenta',
                              textAlign: TextAlign.center,
                              style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
                            ),
                            const SizedBox(height: 8),
                            const Text(
                              'El primer usuario registrado obtendrá rol de Administrador.',
                              textAlign: TextAlign.center,
                              style: TextStyle(fontSize: 13, color: Colors.grey),
                            ),
                            const SizedBox(height: 20),
                            TextFormField(
                              controller: _nameController,
                              decoration: const InputDecoration(
                                labelText: AppStrings.fullName,
                                prefixIcon: Icon(Icons.badge_outlined),
                              ),
                              validator: (v) => (v == null || v.trim().isEmpty) ? AppStrings.fieldRequired : null,
                            ),
                            const SizedBox(height: 16),
                            TextFormField(
                              controller: _emailController,
                              keyboardType: TextInputType.emailAddress,
                              decoration: const InputDecoration(
                                labelText: AppStrings.email,
                                prefixIcon: Icon(Icons.email_outlined),
                              ),
                              validator: (v) => (v == null || v.trim().isEmpty) ? AppStrings.fieldRequired : null,
                            ),
                            const SizedBox(height: 16),
                            TextFormField(
                              controller: _passwordController,
                              obscureText: true,
                              decoration: const InputDecoration(
                                labelText: AppStrings.password,
                                prefixIcon: Icon(Icons.lock_outline),
                              ),
                              validator: (v) => (v == null || v.length < 6) ? 'Mínimo 6 caracteres' : null,
                            ),
                            const SizedBox(height: 24),
                            ListenableBuilder(
                              listenable: widget.authProvider,
                              builder: (ctx, _) {
                                return ElevatedButton(
                                  onPressed: widget.authProvider.isLoading ? null : _submit,
                                  child: widget.authProvider.isLoading
                                      ? const SizedBox(
                                          height: 20,
                                          width: 20,
                                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                        )
                                      : const Text(AppStrings.register),
                                );
                              },
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            );
          }
        }
        """.formatted(pubName, pubName, pubName);
    }

    private String serverSettingsScreen(String pubName) {
        return """
        import 'package:flutter/material.dart';
        import 'package:http/http.dart' as http;
        import 'package:%s/core/config/app_config.dart';
        import 'package:%s/core/i18n/app_strings.dart';

        class ServerSettingsScreen extends StatefulWidget {
          const ServerSettingsScreen({super.key});

          @override
          State<ServerSettingsScreen> createState() => _ServerSettingsScreenState();
        }

        class _ServerSettingsScreenState extends State<ServerSettingsScreen> {
          final _controller = TextEditingController();
          bool _testing = false;
          String? _testResult;
          bool? _testSuccess;

          @override
          void initState() {
            super.initState();
            _load();
          }

          Future<void> _load() async {
            final url = await AppConfig.getBaseUrl();
            _controller.text = url;
            if (mounted) setState(() {});
          }

          Future<void> _test() async {
            setState(() {
              _testing = true;
              _testResult = null;
              _testSuccess = null;
            });
            final raw = _controller.text.trim().replaceAll(RegExp(r'/+$'), '');
            try {
              final uri = Uri.parse('$raw/actuator/health');
              final res = await http.get(uri).timeout(const Duration(seconds: 4));
              if (res.statusCode == 200) {
                setState(() {
                  _testSuccess = true;
                  _testResult = AppStrings.connectionSuccess;
                });
              } else {
                setState(() {
                  _testSuccess = false;
                  _testResult = 'Respuesta inesperada: ${res.statusCode}';
                });
              }
            } catch (e) {
              setState(() {
                _testSuccess = false;
                _testResult = '${AppStrings.connectionFailed}: $e';
              });
            } finally {
              setState(() => _testing = false);
            }
          }

          Future<void> _save() async {
            final raw = _controller.text.trim().replaceAll(RegExp(r'/+$'), '');
            if (raw.isNotEmpty) {
              await AppConfig.setBaseUrl(raw);
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Configuración guardada exitosamente')),
                );
                Navigator.of(context).pop();
              }
            }
          }

          Future<void> _reset() async {
            await AppConfig.resetBaseUrl();
            await _load();
          }

          @override
          Widget build(BuildContext context) {
            return Scaffold(
              appBar: AppBar(title: const Text(AppStrings.serverSettings)),
              body: ListView(
                padding: const EdgeInsets.all(20.0),
                children: [
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(18.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          const Text(
                            'Configuración de Red del Backend',
                            style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold),
                          ),
                          const SizedBox(height: 12),
                          const Text(
                            'Por defecto conecta a http://localhost:8080 configurado con --dart-define. Puedes cambiar la URL para apuntar a la IP de tu PC o a un servidor remoto.',
                            style: TextStyle(fontSize: 13, color: Colors.grey),
                          ),
                          const SizedBox(height: 18),
                          TextFormField(
                            controller: _controller,
                            decoration: const InputDecoration(
                              labelText: AppStrings.serverUrl,
                              hintText: 'http://localhost:8080',
                              prefixIcon: Icon(Icons.dns_outlined),
                            ),
                          ),
                          const SizedBox(height: 16),
                          Row(
                            children: [
                              Expanded(
                                child: OutlinedButton.icon(
                                  onPressed: _testing ? null : _test,
                                  icon: _testing
                                      ? const SizedBox(
                                          width: 16,
                                          height: 16,
                                          child: CircularProgressIndicator(strokeWidth: 2),
                                        )
                                      : const Icon(Icons.wifi_find),
                                  label: const Text(AppStrings.testConnection),
                                ),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: ElevatedButton.icon(
                                  onPressed: _save,
                                  icon: const Icon(Icons.save),
                                  label: const Text(AppStrings.save),
                                ),
                              ),
                            ],
                          ),
                          if (_testResult != null) ...[
                            const SizedBox(height: 14),
                            Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: _testSuccess == true
                                    ? Colors.green.shade50
                                    : Colors.red.shade50,
                                borderRadius: BorderRadius.circular(8),
                                border: Border.all(
                                  color: _testSuccess == true
                                      ? Colors.green.shade300
                                      : Colors.red.shade300,
                                ),
                              ),
                              child: Text(
                                _testResult!,
                                style: TextStyle(
                                  color: _testSuccess == true
                                      ? Colors.green.shade900
                                      : Colors.red.shade900,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ),
                          ],
                          const SizedBox(height: 12),
                          TextButton(
                            onPressed: _reset,
                            child: const Text('Restablecer a valor predeterminado'),
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Card(
                    color: Colors.blue.shade50,
                    child: const Padding(
                      padding: EdgeInsets.all(16.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Icon(Icons.phone_android, color: Color(0xFF1E3A8A)),
                              SizedBox(width: 8),
                              Text(
                                'Conexión con Android Físico por USB',
                                style: TextStyle(
                                  fontWeight: FontWeight.bold,
                                  color: Color(0xFF1E3A8A),
                                ),
                              ),
                            ],
                          ),
                          SizedBox(height: 8),
                          Text(
                            'Para que tu teléfono Samsung conectado por USB alcance el backend en localhost:8080 sin configurar Wi-Fi, ejecuta en tu terminal:\\n\\n'
                            '  adb reverse tcp:8080 tcp:8080\\n\\n'
                            'Si usas conexión Wi-Fi, ingresa la IP local de tu computadora (por ejemplo: http://192.168.1.50:8080).',
                            style: TextStyle(fontSize: 13, height: 1.4),
                          ),
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            );
          }
        }
        """.formatted(pubName, pubName);
    }

    // =========================================================================
    // 5. ENUMERATION TEMPLATES
    // =========================================================================

    private String enumModel(DiagramDocument.Enumeration en) {
        String name = dartName(en.name(), true);
        StringBuilder sb = new StringBuilder();
        sb.append("// ignore_for_file: constant_identifier_names\n\n");
        sb.append("enum ").append(name).append(" {\n");
        for (int i = 0; i < en.values().size(); i++) {
            sb.append("  ").append(en.values().get(i).name());
            sb.append(i + 1 == en.values().size() ? ";\n\n" : ",\n");
        }
        sb.append("  static ").append(name).append(" fromString(dynamic value) {\n");
        sb.append("    final str = value?.toString().toUpperCase() ?? '';\n");
        sb.append("    return ").append(name).append(".values.firstWhere(\n");
        sb.append("      (e) => e.name.toUpperCase() == str,\n");
        sb.append("      orElse: () => ").append(name).append(".values.first,\n");
        sb.append("    );\n");
        sb.append("  }\n\n");
        sb.append("  String toJson() => name;\n");
        sb.append("  String get displayName => name;\n");
        sb.append("}\n");
        return sb.toString();
    }

    // =========================================================================
    // 6. DOMAIN ENTITY TEMPLATES (Model, Repo, Provider, Screens)
    // =========================================================================

    private String entityModel(DiagramDocument diagram, DiagramDocument.ClassElement item, String pubName) {
        String name = dartName(item.name(), true);
        UUID parentId = findParentId(diagram, item.id());
        boolean hasParent = parentId != null;
        String parentName = hasParent ? dartName(findClass(diagram, parentId).name(), true) : null;

        StringBuilder sb = new StringBuilder();
        sb.append("// Modelo tipado para ").append(name).append("\n\n");

        // Imports of enums if used
        for (var attr : item.attributes()) {
            if (isEnum(diagram, attr.type())) {
                sb.append("import 'package:").append(pubName).append("/data/models/").append(sqlName(attr.type())).append(".dart';\n");
            }
        }
        if (hasParent) {
            sb.append("import 'package:").append(pubName).append("/data/models/").append(sqlName(findClass(diagram, parentId).name())).append(".dart';\n");
        }
        sb.append("\n");

        sb.append("class ").append(name);
        if (hasParent) {
            sb.append(" extends ").append(parentName);
        }
        sb.append(" {\n");

        // Primary key if inherited
        if (hasParent && item.attributes().stream().noneMatch(DiagramDocument.Attribute::primaryKey)) {
            // Parent has id
        } else if (!hasParent) {
            var pk = findPk(diagram, item);
            if (pk.isEmpty()) {
                sb.append("  final String id;\n");
            }
        }

        // Own attributes
        for (var attr : item.attributes()) {
            String type = dartType(diagram, attr.type());
            String field = dartName(attr.name(), false);
            sb.append("  final ").append(type).append(" ").append(field).append(";\n");
        }

        // Relation IDs
        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String targetField = dartName(target.name(), false);
                if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("  final String? ").append(targetField).append("Id;\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("  final List<String> ").append(targetField).append("Ids;\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String sourceField = dartName(source.name(), false);
                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("  final String? ").append(sourceField).append("Id;\n");
                }
            }
        }

        sb.append("\n  ").append(name).append("({\n");
        if (hasParent) {
            sb.append("    required super.id,\n");
            var parent = findClass(diagram, parentId);
            for (var pattr : parent.attributes()) {
                if (!pattr.primaryKey()) {
                    sb.append("    required super.").append(dartName(pattr.name(), false)).append(",\n");
                }
            }
        } else {
            var pk = findPk(diagram, item);
            if (pk.isEmpty()) {
                sb.append("    required this.id,\n");
            }
        }
        for (var attr : item.attributes()) {
            sb.append("    required this.").append(dartName(attr.name(), false)).append(",\n");
        }
        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String targetField = dartName(target.name(), false);
                if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    this.").append(targetField).append("Id,\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    this.").append(targetField).append("Ids = const [],\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String sourceField = dartName(source.name(), false);
                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    this.").append(sourceField).append("Id,\n");
                }
            }
        }
        sb.append("  });\n\n");

        // fromJson factory
        sb.append("  factory ").append(name).append(".fromJson(Map<String, dynamic> json) {\n");
        sb.append("    return ").append(name).append("(\n");
        if (hasParent) {
            sb.append("      id: json['id']?.toString() ?? '',\n");
            var parent = findClass(diagram, parentId);
            for (var pattr : parent.attributes()) {
                if (!pattr.primaryKey()) {
                    sb.append("      ").append(dartName(pattr.name(), false)).append(": ")
                      .append(dartParseField(diagram, pattr, "json['" + dartName(pattr.name(), false) + "']")).append(",\n");
                }
            }
        } else {
            var pk = findPk(diagram, item);
            if (pk.isEmpty()) {
                sb.append("      id: json['id']?.toString() ?? '',\n");
            }
        }
        for (var attr : item.attributes()) {
            String field = dartName(attr.name(), false);
            sb.append("      ").append(field).append(": ")
              .append(dartParseField(diagram, attr, "json['" + field + "']")).append(",\n");
        }
        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String targetField = dartName(target.name(), false);
                if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("      ").append(targetField).append("Id: json['").append(targetField).append("Id']?.toString(),\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("      ").append(targetField).append("Ids: (json['").append(targetField).append("Ids'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String sourceField = dartName(source.name(), false);
                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("      ").append(sourceField).append("Id: json['").append(sourceField).append("Id']?.toString(),\n");
                }
            }
        }
        sb.append("    );\n");
        sb.append("  }\n\n");

        // toJson (for Output and Input DTO)
        sb.append("  Map<String, dynamic> toJson() => {\n");
        if (hasParent) {
            sb.append("    'id': id,\n");
            var parent = findClass(diagram, parentId);
            for (var pattr : parent.attributes()) {
                if (!pattr.primaryKey()) {
                    sb.append("    '").append(dartName(pattr.name(), false)).append("': ")
                      .append(dartSerializeValue(diagram, pattr, dartName(pattr.name(), false))).append(",\n");
                }
            }
        } else {
            var pk = findPk(diagram, item);
            if (pk.isEmpty()) {
                sb.append("    'id': id,\n");
            }
        }
        for (var attr : item.attributes()) {
            String field = dartName(attr.name(), false);
            sb.append("    '").append(field).append("': ")
              .append(dartSerializeValue(diagram, attr, field)).append(",\n");
        }
        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String targetField = dartName(target.name(), false);
                if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    '").append(targetField).append("Id': ").append(targetField).append("Id,\n");
                } else if (isManyToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    '").append(targetField).append("Ids': ").append(targetField).append("Ids,\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String sourceField = dartName(source.name(), false);
                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    sb.append("    '").append(sourceField).append("Id': ").append(sourceField).append("Id,\n");
                }
            }
        }
        sb.append("  };\n\n");

        // toInputJson (omits primary keys for POST/PUT)
        sb.append("  Map<String, dynamic> toInputJson() {\n");
        sb.append("    final map = toJson();\n");
        sb.append("    map.remove('id');\n");
        for (var attr : item.attributes()) {
            if (attr.primaryKey()) {
                sb.append("    map.remove('").append(dartName(attr.name(), false)).append("');\n");
            }
        }
        sb.append("    return map;\n");
        sb.append("  }\n\n");

        // Display label helper
        sb.append("  String get displayLabel {\n");
        var displayAttr = item.attributes().stream().filter(a -> !a.primaryKey() && "String".equals(a.type())).findFirst();
        if (displayAttr.isPresent()) {
            sb.append("    return ").append(dartName(displayAttr.get().name(), false)).append(";\n");
        } else {
            sb.append("    return '$").append(getPkFieldName(diagram, item)).append("';\n");
        }
        sb.append("  }\n");

        sb.append("}\n");
        return sb.toString();
    }

    private String entityRepository(DiagramDocument diagram, DiagramDocument.ClassElement item, String pubName) {
        String name = dartName(item.name(), true);
        String resource = sqlName(item.name());
        String fileName = sqlName(item.name());

        return """
        import 'package:uuid/uuid.dart';
        import 'package:%s/core/api/api_client.dart';
        import 'package:%s/core/database/app_database.dart';
        import 'package:%s/core/sync/outbox_service.dart';
        import 'package:%s/core/sync/sync_service.dart';
        import 'package:%s/data/models/%s.dart';

        class %sRepository {
          final ApiClient _client = ApiClient.instance;
          final AppDatabase _db = AppDatabase.instance;
          final OutboxService _outbox = OutboxService.instance;
          final SyncService _sync = SyncService.instance;
          static const String _entity = '%s';
          static const Uuid _uuid = Uuid();

          Future<List<%s>> getAll({int page = 0, int size = 20, String? search}) async {
            if (_sync.isOnline) {
              _sync.syncDown().catchError((_) {});
            }

            final localRows = await _db.getCachedEntities(_entity);
            List<%s> items = localRows.map((e) => %s.fromJson(e)).toList();

            if (items.isEmpty && _sync.isOnline) {
              try {
                final query = <String, String>{};
                if (search != null && search.trim().isNotEmpty) {
                  query['search'] = search.trim();
                }
                final data = await _client.get('/api/%s', queryParams: query);
                if (data is List) {
                  for (final item in data) {
                    if (item is Map<String, dynamic>) {
                      final id = item['id']?.toString() ?? _uuid.v4();
                      await _db.saveCachedEntity(_entity, id, item, syncStatus: 'synced');
                    }
                  }
                  final refreshed = await _db.getCachedEntities(_entity);
                  items = refreshed.map((e) => %s.fromJson(e)).toList();
                }
              } catch (_) {}
            }

            if (search != null && search.trim().isNotEmpty) {
              final s = search.trim().toLowerCase();
              items = items.where((i) => i.displayLabel.toLowerCase().contains(s)).toList();
            }
            return items;
          }

          Future<%s> getById(String id) async {
            final local = await _db.getCachedEntity(_entity, id);
            if (local != null) {
              return %s.fromJson(local);
            }
            final data = await _client.get('/api/%s/$id') as Map<String, dynamic>;
            final item = %s.fromJson(data);
            await _db.saveCachedEntity(_entity, id, data, syncStatus: 'synced');
            return item;
          }

          Future<%s> create(%s item) async {
            final id = item.id.isNotEmpty ? item.id : _uuid.v4();
            final json = item.toJson()..['id'] = id;
            final createdItem = %s.fromJson(json);

            await _outbox.enqueueCreate(
              entity: _entity,
              recordId: id,
              payload: createdItem.toInputJson(),
            );

            if (_sync.isOnline) {
              _sync.syncUp().catchError((_) {});
            }

            return createdItem;
          }

          Future<%s> update(String id, %s item) async {
            final cached = await _db.getCachedEntity(_entity, id);
            final baseVersion = (cached?['_version'] as int?) ?? 1;

            final json = item.toJson()..['id'] = id;
            final updatedItem = %s.fromJson(json);

            await _outbox.enqueueUpdate(
              entity: _entity,
              recordId: id,
              payload: updatedItem.toInputJson(),
              baseVersion: baseVersion,
            );

            if (_sync.isOnline) {
              _sync.syncUp().catchError((_) {});
            }

            return updatedItem;
          }

          Future<void> delete(String id) async {
            final cached = await _db.getCachedEntity(_entity, id);
            final baseVersion = (cached?['_version'] as int?) ?? 1;

            await _outbox.enqueueDelete(
              entity: _entity,
              recordId: id,
              baseVersion: baseVersion,
            );

            if (_sync.isOnline) {
              _sync.syncUp().catchError((_) {});
            }
          }
        }
        """.formatted(
            pubName, pubName, pubName, pubName, pubName, fileName,
            name, resource,
            name, name, name, resource, name,
            name, name, resource, name,
            name, name, name,
            name, name, name
        );
    }

    private String entityProvider(DiagramDocument diagram, DiagramDocument.ClassElement item, String pubName) {
        String name = dartName(item.name(), true);
        String fileName = sqlName(item.name());

        return """
        import 'package:flutter/material.dart';
        import 'package:%s/data/models/%s.dart';
        import 'package:%s/data/repositories/%s_repository.dart';

        class %sProvider extends ChangeNotifier {
          final %sRepository _repository = %sRepository();

          List<%s> _items = [];
          bool _isLoading = false;
          bool _isSaving = false;
          String? _errorMessage;
          String _searchQuery = '';

          List<%s> get items {
            if (_searchQuery.isEmpty) return _items;
            return _items.where((i) => i.displayLabel.toLowerCase().contains(_searchQuery.toLowerCase())).toList();
          }

          bool get isLoading => _isLoading;
          bool get isSaving => _isSaving;
          String? get errorMessage => _errorMessage;
          String get searchQuery => _searchQuery;

          void setSearchQuery(String query) {
            _searchQuery = query.trim();
            notifyListeners();
          }

          Future<void> loadItems() async {
            _isLoading = true;
            _errorMessage = null;
            notifyListeners();
            try {
              _items = await _repository.getAll(search: _searchQuery);
              _isLoading = false;
              notifyListeners();
            } catch (e) {
              _errorMessage = e.toString();
              _isLoading = false;
              notifyListeners();
            }
          }

          Future<%s?> saveItem(%s item, {String? id}) async {
            _isSaving = true;
            _errorMessage = null;
            notifyListeners();
            try {
              %s saved;
              if (id != null && id.isNotEmpty) {
                saved = await _repository.update(id, item);
                final idx = _items.indexWhere((it) => it.id == id);
                if (idx != -1) {
                  _items[idx] = saved;
                }
              } else {
                saved = await _repository.create(item);
                _items.insert(0, saved);
              }
              _isSaving = false;
              notifyListeners();
              return saved;
            } catch (e) {
              _errorMessage = e.toString();
              _isSaving = false;
              notifyListeners();
              return null;
            }
          }

          Future<bool> deleteItem(String id) async {
            try {
              await _repository.delete(id);
              _items.removeWhere((it) => it.id == id);
              notifyListeners();
              return true;
            } catch (e) {
              _errorMessage = e.toString();
              notifyListeners();
              return false;
            }
          }
        }
        """.formatted(pubName, fileName, pubName, fileName, name, name, name, name, name, name, name, name);
    }

    private String entityListScreen(DiagramDocument diagram, DiagramDocument.ClassElement item, String pubName) {
        String name = dartName(item.name(), true);
        String fileName = sqlName(item.name());
        String pkField = getPkFieldName(diagram, item);

        return """
        import 'package:flutter/material.dart';
        import 'package:%s/core/widgets/async_state_view.dart';
        import 'package:%s/core/widgets/confirm_dialog.dart';
        import 'package:%s/core/i18n/app_strings.dart';
        import 'package:%s/data/models/%s.dart';
        import 'package:%s/presentation/state/%s_provider.dart';
        import 'package:%s/presentation/screens/%s/%s_form_screen.dart';
        import 'package:%s/presentation/screens/%s/%s_detail_screen.dart';

        class %sListScreen extends StatefulWidget {
          const %sListScreen({super.key});

          @override
          State<%sListScreen> createState() => _%sListScreenState();
        }

        class _%sListScreenState extends State<%sListScreen> {
          final %sProvider _provider = %sProvider();
          final TextEditingController _searchController = TextEditingController();

          @override
          void initState() {
            super.initState();
            _provider.loadItems();
          }

          @override
          void dispose() {
            _searchController.dispose();
            _provider.dispose();
            super.dispose();
          }

          @override
          Widget build(BuildContext context) {
            return Scaffold(
              appBar: AppBar(
                title: const Text('%s'),
                actions: [
                  IconButton(
                    icon: const Icon(Icons.refresh),
                    tooltip: AppStrings.retry,
                    onPressed: () => _provider.loadItems(),
                  ),
                ],
              ),
              floatingActionButton: FloatingActionButton.extended(
                onPressed: () async {
                  final created = await Navigator.of(context).push<%s>(
                    MaterialPageRoute(builder: (_) => const %sFormScreen()),
                  );
                  if (created != null) {
                    _provider.loadItems();
                  }
                },
                icon: const Icon(Icons.add),
                label: const Text('Nuevo %s'),
              ),
              body: Column(
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                    color: Colors.white,
                    child: TextField(
                      controller: _searchController,
                      decoration: InputDecoration(
                        hintText: 'Buscar en %s...',
                        prefixIcon: const Icon(Icons.search),
                        suffixIcon: _searchController.text.isNotEmpty
                            ? IconButton(
                                icon: const Icon(Icons.clear),
                                onPressed: () {
                                  _searchController.clear();
                                  _provider.setSearchQuery('');
                                },
                              )
                            : null,
                      ),
                      onChanged: (val) => _provider.setSearchQuery(val),
                    ),
                  ),
                  Expanded(
                    child: ListenableBuilder(
                      listenable: _provider,
                      builder: (ctx, _) {
                        return AsyncStateView(
                          isLoading: _provider.isLoading,
                          errorMessage: _provider.errorMessage,
                          isEmpty: _provider.items.isEmpty,
                          onRetry: () => _provider.loadItems(),
                          child: RefreshIndicator(
                            onRefresh: () => _provider.loadItems(),
                            child: ListView.builder(
                              itemCount: _provider.items.length,
                              padding: const EdgeInsets.only(bottom: 80, top: 8),
                              itemBuilder: (context, index) {
                                final item = _provider.items[index];
                                return Card(
                                  child: ListTile(
                                    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                                    leading: const CircleAvatar(
                                      backgroundColor: Color(0x1F1E3A8A),
                                      child: Icon(Icons.folder_outlined, color: Color(0xFF1E3A8A)),
                                    ),
                                    title: Text(
                                      item.displayLabel,
                                      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                                    ),
                                    subtitle: Text(
                                      'ID: ${item.%s}',
                                      style: const TextStyle(fontSize: 12, color: Colors.grey),
                                    ),
                                    trailing: Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        IconButton(
                                          icon: const Icon(Icons.edit, color: Colors.blue),
                                          onPressed: () async {
                                            final updated = await Navigator.of(context).push<%s>(
                                              MaterialPageRoute(builder: (_) => %sFormScreen(item: item)),
                                            );
                                            if (updated != null) {
                                              _provider.loadItems();
                                            }
                                          },
                                        ),
                                        IconButton(
                                          icon: const Icon(Icons.delete_outline, color: Colors.red),
                                          onPressed: () async {
                                            final confirm = await ConfirmDialog.show(context);
                                            if (confirm) {
                                              await _provider.deleteItem(item.%s);
                                            }
                                          },
                                        ),
                                      ],
                                    ),
                                    onTap: () async {
                                      await Navigator.of(context).push(
                                        MaterialPageRoute(builder: (_) => %sDetailScreen(item: item)),
                                      );
                                      _provider.loadItems();
                                    },
                                  ),
                                );
                              },
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                ],
              ),
            );
          }
        }
        """.formatted(
            pubName, pubName, pubName, pubName, fileName, pubName, fileName,
            pubName, fileName, fileName, pubName, fileName, fileName,
            name, name, name, name, name, name, name, name, name,
            name, name, name, name, pkField, name, name, pkField, name
        );
    }

    private String entityDetailScreen(DiagramDocument diagram, DiagramDocument.ClassElement item, String pubName) {
        String name = dartName(item.name(), true);
        String fileName = sqlName(item.name());
        String pkField = getPkFieldName(diagram, item);

        StringBuilder attrs = new StringBuilder();
        for (var attr : item.attributes()) {
            String field = dartName(attr.name(), false);
            if ("String".equals(attr.type())) {
                attrs.append("                  _buildRow('").append(attr.name()).append("', item.").append(field).append("),\n");
            } else {
                attrs.append("                  _buildRow('").append(attr.name()).append("', item.").append(field).append(".toString()),\n");
            }
        }
        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String targetField = dartName(target.name(), false);
                if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    attrs.append("                  _buildRow('Relación ").append(target.name()).append(" ID', item.")
                         .append(targetField).append("Id ?? '-'),\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String sourceField = dartName(source.name(), false);
                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    attrs.append("                  _buildRow('Relación ").append(source.name()).append(" ID', item.")
                         .append(sourceField).append("Id ?? '-'),\n");
                }
            }
        }

        return """
        import 'package:flutter/material.dart';
        import 'package:%s/core/widgets/confirm_dialog.dart';
        import 'package:%s/data/models/%s.dart';
        import 'package:%s/data/repositories/%s_repository.dart';
        import 'package:%s/presentation/screens/%s/%s_form_screen.dart';

        class %sDetailScreen extends StatefulWidget {
          final %s item;
          const %sDetailScreen({super.key, required this.item});

          @override
          State<%sDetailScreen> createState() => _%sDetailScreenState();
        }

        class _%sDetailScreenState extends State<%sDetailScreen> {
          late %s item;
          final %sRepository _repo = %sRepository();

          @override
          void initState() {
            super.initState();
            item = widget.item;
          }

          Widget _buildRow(String label, String value) {
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 8.0),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(
                    width: 140,
                    child: Text(label, style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.grey)),
                  ),
                  Expanded(
                    child: Text(value, style: const TextStyle(fontSize: 15)),
                  ),
                ],
              ),
            );
          }

          @override
          Widget build(BuildContext context) {
            return Scaffold(
              appBar: AppBar(
                title: Text('Detalle de ${item.displayLabel}'),
                actions: [
                  IconButton(
                    icon: const Icon(Icons.edit),
                    onPressed: () async {
                      final updated = await Navigator.of(context).push<%s>(
                        MaterialPageRoute(builder: (_) => %sFormScreen(item: item)),
                      );
                      if (updated != null) {
                        setState(() => item = updated);
                      }
                    },
                  ),
                  IconButton(
                    icon: const Icon(Icons.delete),
                    onPressed: () async {
                      final confirm = await ConfirmDialog.show(context);
                      if (confirm && mounted) {
                        await _repo.delete(item.%s);
                        Navigator.of(context).pop();
                      }
                    },
                  ),
                ],
              ),
              body: ListView(
                padding: const EdgeInsets.all(16.0),
                children: [
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(18.0),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              const Icon(Icons.info_outline, color: Color(0xFF1E3A8A)),
                              const SizedBox(width: 8),
                              Text(
                                item.displayLabel,
                                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                              ),
                            ],
                          ),
                          const Divider(height: 24),
        %s
                        ],
                      ),
                    ),
                  ),
                ],
              ),
            );
          }
        }
        """.formatted(
            pubName, pubName, fileName, pubName, fileName, pubName, fileName, fileName,
            name, name, name, name, name, name, name, name, name, name, name, name, pkField, attrs.toString()
        );
    }

    private String entityFormScreen(DiagramDocument diagram, DiagramDocument.ClassElement item, String pubName) {
        String name = dartName(item.name(), true);
        String fileName = sqlName(item.name());
        String pkField = getPkFieldName(diagram, item);
        UUID parentId = findParentId(diagram, item.id());
        boolean hasParent = parentId != null;

        StringBuilder controllers = new StringBuilder();
        StringBuilder initControllers = new StringBuilder();
        StringBuilder formWidgets = new StringBuilder();
        StringBuilder constructArgs = new StringBuilder();

        StringBuilder relationLoaders = new StringBuilder();
        StringBuilder relationState = new StringBuilder();

        Set<String> extraImports = new LinkedHashSet<>();
        boolean hasRelationPicker = false;

        // 1. Attributes
        for (var attr : item.attributes()) {
            String field = dartName(attr.name(), false);
            String type = attr.type();

            if (attr.primaryKey()) continue;

            if (isEnum(diagram, type)) {
                String enumType = dartName(type, true);
                extraImports.add("import 'package:" + pubName + "/data/models/" + sqlName(type) + ".dart';");
                controllers.append("  ").append(enumType).append("? _").append(field).append(";\n");
                initControllers.append("    _").append(field).append(" = widget.item?.").append(field)
                               .append(" ?? ").append(enumType).append(".values.first;\n");
                formWidgets.append("          DropdownButtonFormField<").append(enumType).append(">(\n")
                           .append("            initialValue: _").append(field).append(",\n")
                           .append("            decoration: const InputDecoration(labelText: '").append(attr.name()).append("'),\n")
                           .append("            items: ").append(enumType).append(".values.map((e) => DropdownMenuItem(value: e, child: Text(e.displayName))).toList(),\n")
                           .append("            onChanged: (val) => setState(() => _").append(field).append(" = val),\n")
                           .append("          ),\n          const SizedBox(height: 16),\n");
                constructArgs.append("      ").append(field).append(": _").append(field).append(" ?? ").append(enumType).append(".values.first,\n");
            } else if ("Boolean".equals(type)) {
                controllers.append("  bool _").append(field).append(" = false;\n");
                initControllers.append("    _").append(field).append(" = widget.item?.").append(field).append(" ?? false;\n");
                formWidgets.append("          SwitchListTile(\n")
                           .append("            title: const Text('").append(attr.name()).append("'),\n")
                           .append("            value: _").append(field).append(",\n")
                           .append("            onChanged: (val) => setState(() => _").append(field).append(" = val),\n")
                           .append("          ),\n          const SizedBox(height: 16),\n");
                constructArgs.append("      ").append(field).append(": _").append(field).append(",\n");
            } else if ("Date".equals(type) || "DateTime".equals(type)) {
                controllers.append("  DateTime? _").append(field).append(";\n");
                initControllers.append("    _").append(field).append(" = widget.item?.").append(field).append(" ?? DateTime.now();\n");
                formWidgets.append("          ListTile(\n")
                           .append("            contentPadding: EdgeInsets.zero,\n")
                           .append("            title: Text('").append(attr.name()).append(": ${_").append(field).append("?.toIso8601String().split('T').first ?? 'Sin fecha'}'),\n")
                           .append("            trailing: const Icon(Icons.calendar_today),\n")
                           .append("            onTap: () async {\n")
                           .append("              final picked = await showDatePicker(context: context, initialDate: _").append(field).append(" ?? DateTime.now(), firstDate: DateTime(1900), lastDate: DateTime(2100));\n")
                           .append("              if (picked != null) setState(() => _").append(field).append(" = picked);\n")
                           .append("            },\n")
                           .append("          ),\n          const SizedBox(height: 16),\n");
                constructArgs.append("      ").append(field).append(": _").append(field).append(" ?? DateTime.now(),\n");
            } else {
                controllers.append("  final _").append(field).append("Controller = TextEditingController();\n");
                initControllers.append("    if (widget.item != null) _").append(field).append("Controller.text = widget.item!.")
                               .append(field).append(".toString();\n");
                boolean isNumber = "Integer".equals(type) || "Long".equals(type) || "Decimal".equals(type);
                formWidgets.append("          TextFormField(\n")
                           .append("            controller: _").append(field).append("Controller,\n")
                           .append("            decoration: const InputDecoration(labelText: '").append(attr.name()).append("'),\n")
                           .append(isNumber ? "            keyboardType: TextInputType.number,\n" : "")
                           .append("            validator: (val) {\n");
                if (attr.required()) {
                    formWidgets.append("              if (val == null || val.trim().isEmpty) return AppStrings.fieldRequired;\n");
                    if (isNumber) {
                        formWidgets.append("              if (num.tryParse(val) == null) return AppStrings.invalidNumber;\n");
                    }
                } else if (isNumber) {
                    formWidgets.append("              if (val != null && val.trim().isNotEmpty && num.tryParse(val) == null) return AppStrings.invalidNumber;\n");
                }
                formWidgets.append("              return null;\n")
                           .append("            },\n")
                           .append("          ),\n          const SizedBox(height: 16),\n");

                if ("Integer".equals(type) || "Long".equals(type)) {
                    constructArgs.append("      ").append(field).append(": int.tryParse(_").append(field).append("Controller.text) ?? 0,\n");
                } else if ("Decimal".equals(type)) {
                    constructArgs.append("      ").append(field).append(": double.tryParse(_").append(field).append("Controller.text) ?? 0.0,\n");
                } else {
                    constructArgs.append("      ").append(field).append(": _").append(field).append("Controller.text.trim(),\n");
                }
            }
        }

        // 2. Relation Selectors
        for (var assoc : diagram.associations()) {
            if (assoc.sourceId().equals(item.id())) {
                var target = findClass(diagram, assoc.targetId());
                String targetClass = dartName(target.name(), true);
                String targetField = dartName(target.name(), false);

                if (isManyToOne(assoc.sourceCardinality(), assoc.targetCardinality()) || isOneToOne(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    hasRelationPicker = true;
                    extraImports.add("import 'package:" + pubName + "/data/models/" + sqlName(target.name()) + ".dart';");
                    extraImports.add("import 'package:" + pubName + "/data/repositories/" + sqlName(target.name()) + "_repository.dart';");

                    controllers.append("  String? _").append(targetField).append("Id;\n");
                    initControllers.append("    _").append(targetField).append("Id = widget.item?.").append(targetField).append("Id;\n");
                    relationState.append("  List<").append(targetClass).append("> _").append(targetField).append("Options = [];\n");
                    relationLoaders.append("    ").append(targetClass).append("Repository().getAll().then((list) {\n")
                                   .append("      if (mounted) setState(() => _").append(targetField).append("Options = list);\n")
                                   .append("    });\n");

                    formWidgets.append("          RelationDropdown<").append(targetClass).append(">(\n")
                               .append("            label: 'Seleccionar ").append(target.name()).append("',\n")
                               .append("            value: _").append(targetField).append("Options.where((e) => e.id == _").append(targetField).append("Id).firstOrNull,\n")
                               .append("            items: _").append(targetField).append("Options,\n")
                               .append("            itemLabel: (e) => e.displayLabel,\n")
                               .append("            itemValue: (e) => e.id,\n")
                               .append("            onChanged: (val) => setState(() => _").append(targetField).append("Id = val?.id),\n")
                               .append("          ),\n          const SizedBox(height: 16),\n");

                    constructArgs.append("      ").append(targetField).append("Id: _").append(targetField).append("Id,\n");
                }
            } else if (assoc.targetId().equals(item.id())) {
                var source = findClass(diagram, assoc.sourceId());
                String sourceClass = dartName(source.name(), true);
                String sourceField = dartName(source.name(), false);

                if (isOneToMany(assoc.sourceCardinality(), assoc.targetCardinality())) {
                    hasRelationPicker = true;
                    extraImports.add("import 'package:" + pubName + "/data/models/" + sqlName(source.name()) + ".dart';");
                    extraImports.add("import 'package:" + pubName + "/data/repositories/" + sqlName(source.name()) + "_repository.dart';");

                    controllers.append("  String? _").append(sourceField).append("Id;\n");
                    initControllers.append("    _").append(sourceField).append("Id = widget.item?.").append(sourceField).append("Id;\n");
                    relationState.append("  List<").append(sourceClass).append("> _").append(sourceField).append("Options = [];\n");
                    relationLoaders.append("    ").append(sourceClass).append("Repository().getAll().then((list) {\n")
                                   .append("      if (mounted) setState(() => _").append(sourceField).append("Options = list);\n")
                                   .append("    });\n");

                    formWidgets.append("          RelationDropdown<").append(sourceClass).append(">(\n")
                               .append("            label: 'Seleccionar ").append(source.name()).append("',\n")
                               .append("            value: _").append(sourceField).append("Options.where((e) => e.id == _").append(sourceField).append("Id).firstOrNull,\n")
                               .append("            items: _").append(sourceField).append("Options,\n")
                               .append("            itemLabel: (e) => e.displayLabel,\n")
                               .append("            itemValue: (e) => e.id,\n")
                               .append("            onChanged: (val) => setState(() => _").append(sourceField).append("Id = val?.id),\n")
                               .append("          ),\n          const SizedBox(height: 16),\n");

                    constructArgs.append("      ").append(sourceField).append("Id: _").append(sourceField).append("Id,\n");
                }
            }
        }

        String pickerImport = hasRelationPicker ? "import 'package:" + pubName + "/core/widgets/relation_picker.dart';\n" : "";
        String extraImportsStr = String.join("\n", extraImports);
        if (!extraImportsStr.isEmpty()) extraImportsStr += "\n";

        return """
        import 'package:flutter/material.dart';
        import 'package:%s/core/i18n/app_strings.dart';
        %simport 'package:%s/data/models/%s.dart';
        import 'package:%s/data/repositories/%s_repository.dart';
        %s
        class %sFormScreen extends StatefulWidget {
          final %s? item;
          const %sFormScreen({super.key, this.item});

          @override
          State<%sFormScreen> createState() => _%sFormScreenState();
        }

        class _%sFormScreenState extends State<%sFormScreen> {
          final _formKey = GlobalKey<FormState>();
          final %sRepository _repository = %sRepository();
          bool _saving = false;

        %s
        %s

          @override
          void initState() {
            super.initState();
        %s
        %s
          }

          Future<void> _submit() async {
            if (!_formKey.currentState!.validate()) return;
            setState(() => _saving = true);
            try {
              final payload = %s(
                id: widget.item?.%s ?? '',
        %s      );
              %s saved;
              if (widget.item != null) {
                saved = await _repository.update(widget.item!.%s, payload);
              } else {
                saved = await _repository.create(payload);
              }
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text(AppStrings.savedSuccess)),
                );
                Navigator.of(context).pop(saved);
              }
            } catch (e) {
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(content: Text(e.toString()), backgroundColor: Colors.red),
                );
              }
            } finally {
              if (mounted) setState(() => _saving = false);
            }
          }

          @override
          Widget build(BuildContext context) {
            final isEdit = widget.item != null;
            return Scaffold(
              appBar: AppBar(
                title: Text(isEdit ? 'Editar %s' : 'Nuevo %s'),
              ),
              body: SingleChildScrollView(
                padding: const EdgeInsets.all(16.0),
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.all(20.0),
                    child: Form(
                      key: _formKey,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
        %s
                          const SizedBox(height: 12),
                          ElevatedButton(
                            onPressed: _saving ? null : _submit,
                            child: _saving
                                ? const SizedBox(
                                    height: 20,
                                    width: 20,
                                    child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                                  )
                                : const Text(AppStrings.save),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            );
          }
        }
        """.formatted(
            pubName, pickerImport, pubName, fileName, pubName, fileName, extraImportsStr,
            name, name, name, name, name, name, name, name, name,
            controllers.toString(), relationState.toString(),
            initControllers.toString(), relationLoaders.toString(),
            name, pkField, constructArgs.toString(),
            name, pkField, name, name, formWidgets.toString()
        );
    }

    // =========================================================================
    // 7. HOME DASHBOARD & MAIN ENTRYPOINT
    // =========================================================================

    private String homeScreen(DiagramDocument diagram, String appTitle, String pubName) {
        StringBuilder cards = new StringBuilder();

        for (var item : diagram.classes()) {
            String name = dartName(item.name(), true);
            String filePrefix = sqlName(item.name());
            cards.append("""
                  Card(
                    child: ListTile(
                      contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
                      leading: const CircleAvatar(
                        backgroundColor: Color(0x1F1E3A8A),
                        child: Icon(Icons.table_chart_outlined, color: Color(0xFF1E3A8A)),
                      ),
                      title: Text(
                        '%s',
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
                      ),
                      subtitle: Text(
                        'Tabla: %s • Atributos: %d',
                        style: const TextStyle(fontSize: 13, color: Colors.grey),
                      ),
                      trailing: const Icon(Icons.chevron_right),
                      onTap: () => Navigator.of(context).push(
                        MaterialPageRoute(builder: (_) => const %sListScreen()),
                      ),
                    ),
                  ),
            """.formatted(name, sqlName(item.name()), item.attributes().size(), name));
        }

        StringBuilder imports = new StringBuilder();
        for (var item : diagram.classes()) {
            String filePrefix = sqlName(item.name());
            imports.append("import 'package:").append(pubName).append("/presentation/screens/").append(filePrefix).append("/").append(filePrefix).append("_list_screen.dart';\n");
        }

        return """
        // ignore_for_file: prefer_const_constructors

        import 'package:flutter/material.dart';
        import 'package:%s/core/i18n/app_strings.dart';
        import 'package:%s/presentation/state/auth_provider.dart';
        import 'package:%s/presentation/screens/auth/login_screen.dart';
        import 'package:%s/presentation/screens/settings/server_settings_screen.dart';
        import 'package:%s/core/sync/sync_service.dart';
        import 'package:%s/core/widgets/sync_status_badge.dart';
        import 'package:%s/presentation/screens/conflicts/conflict_resolution_screen.dart';
        %s

        class HomeScreen extends StatelessWidget {
          final AuthProvider authProvider;
          const HomeScreen({super.key, required this.authProvider});

          @override
          Widget build(BuildContext context) {
            final user = authProvider.user;
            return Scaffold(
              appBar: AppBar(
                title: const Text(AppStrings.appName),
                actions: [
                  const SyncStatusBadge(),
                  IconButton(
                    icon: const Icon(Icons.settings),
                    tooltip: AppStrings.serverSettings,
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute(builder: (_) => const ServerSettingsScreen()),
                    ),
                  ),
                  IconButton(
                    icon: const Icon(Icons.logout),
                    tooltip: AppStrings.logout,
                    onPressed: () async {
                      await authProvider.logout();
                      if (context.mounted) {
                        Navigator.of(context).pushReplacement(
                          MaterialPageRoute(builder: (_) => LoginScreen(authProvider: authProvider)),
                        );
                      }
                    },
                  ),
                ],
              ),
              body: ListView(
                padding: const EdgeInsets.symmetric(vertical: 16),
                children: [
                  AnimatedBuilder(
                    animation: SyncService.instance,
                    builder: (context, _) {
                      final sync = SyncService.instance;
                      if (sync.conflictCount == 0) return const SizedBox.shrink();
                      return Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                        child: Material(
                          color: Colors.red.shade50,
                          borderRadius: BorderRadius.circular(12),
                          child: InkWell(
                            onTap: () {
                              Navigator.push(
                                context,
                                MaterialPageRoute(builder: (_) => const ConflictResolutionScreen()),
                              );
                            },
                            borderRadius: BorderRadius.circular(12),
                            child: Padding(
                              padding: const EdgeInsets.all(12),
                              child: Row(
                                children: [
                                  Icon(Icons.warning_amber_rounded, color: Colors.red.shade700, size: 28),
                                  const SizedBox(width: 12),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          '¡${sync.conflictCount} conflicto(s) de sincronización!',
                                          style: TextStyle(
                                            color: Colors.red.shade900,
                                            fontWeight: FontWeight.bold,
                                            fontSize: 14,
                                          ),
                                        ),
                                        Text(
                                          'Toca para comparar y resolver.',
                                          style: TextStyle(
                                            color: Colors.red.shade800,
                                            fontSize: 12,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                  Icon(Icons.chevron_right, color: Colors.red.shade700),
                                ],
                              ),
                            ),
                          ),
                        ),
                      );
                    },
                  ),
                  if (user != null)
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                      child: Container(
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          gradient: const LinearGradient(
                            colors: [Color(0xFF1E3A8A), Color(0xFF2563EB)],
                            begin: Alignment.topLeft,
                            end: Alignment.bottomRight,
                          ),
                          borderRadius: BorderRadius.circular(12),
                        ),
                        child: Row(
                          children: [
                            CircleAvatar(
                              radius: 24,
                              backgroundColor: Colors.white24,
                              child: Text(
                                user.fullName.isNotEmpty ? user.fullName[0].toUpperCase() : 'U',
                                style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold),
                              ),
                            ),
                            const SizedBox(width: 14),
                            Expanded(
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    user.fullName,
                                    style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
                                  ),
                                  Text(
                                    '${user.email} (${user.role})',
                                    style: const TextStyle(color: Colors.white70, fontSize: 12),
                                  ),
                                ],
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  const Padding(
                    padding: EdgeInsets.fromLTRB(18, 16, 18, 8),
                    child: Text(
                      'Entidades del Modelo',
                      style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Color(0xFF1E3A8A)),
                    ),
                  ),
        %s
                ],
              ),
            );
          }
        }
        """.formatted(pubName, pubName, pubName, pubName, pubName, pubName, pubName, imports.toString(), cards.toString());
    }

    private String mainEntrypoint(DiagramDocument diagram, String title, String pubName) {
        return """
        import 'package:flutter/material.dart';
        import 'package:flutter_localizations/flutter_localizations.dart';
        import 'package:%s/core/theme/app_theme.dart';
        import 'package:%s/core/database/app_database.dart';
        import 'package:%s/core/sync/sync_service.dart';
        import 'package:%s/presentation/state/auth_provider.dart';
        import 'package:%s/presentation/screens/auth/login_screen.dart';
        import 'package:%s/presentation/screens/home/home_screen.dart';

        void main() async {
          WidgetsFlutterBinding.ensureInitialized();
          await AppDatabase.instance.database;
          final authProvider = AuthProvider();
          await authProvider.init();
          await SyncService.instance.init();
          runApp(CollabModelerApp(authProvider: authProvider));
        }

        class CollabModelerApp extends StatelessWidget {
          final AuthProvider authProvider;
          const CollabModelerApp({super.key, required this.authProvider});

          @override
          Widget build(BuildContext context) {
            return MaterialApp(
              title: '%s',
              debugShowCheckedModeBanner: false,
              theme: AppTheme.lightTheme,
              localizationsDelegates: const [
                GlobalMaterialLocalizations.delegate,
                GlobalWidgetsLocalizations.delegate,
                GlobalCupertinoLocalizations.delegate,
              ],
              supportedLocales: const [
                Locale('es', 'ES'),
                Locale('es', ''),
                Locale('en', ''),
              ],
              home: ListenableBuilder(
                listenable: authProvider,
                builder: (ctx, _) {
                  if (authProvider.isAuthenticated) {
                    return HomeScreen(authProvider: authProvider);
                  }
                  return LoginScreen(authProvider: authProvider);
                },
              ),
            );
          }
        }
        """.formatted(pubName, pubName, pubName, pubName, pubName, pubName, title);
    }

    // =========================================================================
    // 8. TEST TEMPLATES
    // =========================================================================

    private String modelTest(DiagramDocument diagram, String pubName) {
        StringBuilder testCases = new StringBuilder();

        for (var item : diagram.classes()) {
            String name = dartName(item.name(), true);
            String fileName = sqlName(item.name());
            testCases.append("""
            test('%s serializes and deserializes JSON correctly', () {
              final json = <String, dynamic>{
                'id': 'test-uuid-1',
              };
              final instance = %s.fromJson(json);
              expect(instance.id, 'test-uuid-1');
              expect(instance.toJson()['id'], 'test-uuid-1');
            });
            """.formatted(name, name));
        }

        StringBuilder imports = new StringBuilder();
        for (var item : diagram.classes()) {
            String fileName = sqlName(item.name());
            imports.append("import 'package:").append(pubName).append("/data/models/").append(fileName).append(".dart';\n");
        }

        return """
        import 'package:flutter_test/flutter_test.dart';
        %s

        void main() {
          group('Domain Models Unit Tests', () {
        %s
          });
        }
        """.formatted(imports.toString(), testCases.toString());
    }

    private String widgetTest(String pubName) {
        return """
        import 'package:flutter_test/flutter_test.dart';
        import 'package:shared_preferences/shared_preferences.dart';
        import 'package:%s/main.dart';
        import 'package:%s/presentation/state/auth_provider.dart';

        void main() {
          setUp(() {
            SharedPreferences.setMockInitialValues({});
          });

          testWidgets('CollabModelerApp launches and displays login or home screen', (WidgetTester tester) async {
            final authProvider = AuthProvider();
            await tester.pumpWidget(CollabModelerApp(authProvider: authProvider));
            expect(find.byType(CollabModelerApp), findsOneWidget);
          });
        }
        """.formatted(pubName, pubName);
    }

    private String offlineSyncTest(DiagramDocument diagram, String pubName) {
        String sampleEntity = diagram.classes().isEmpty() ? "item" : sqlName(diagram.classes().get(0).name());
        return """
        import 'package:flutter_test/flutter_test.dart';
        import 'package:sqflite_common_ffi/sqflite_ffi.dart';
        import 'package:%s/core/database/app_database.dart';
        import 'package:%s/core/storage/secure_storage_service.dart';
        import 'package:%s/core/sync/outbox_service.dart';
        import 'package:%s/core/sync/sync_service.dart';

        void main() {
          TestWidgetsFlutterBinding.ensureInitialized();

          sqfliteFfiInit();
          databaseFactory = databaseFactoryFfi;

          group('Offline-first & Synchronization Tests', () {
            late AppDatabase db;
            late OutboxService outbox;
            late SyncService sync;
            late SecureStorageService secureStorage;

            setUp(() async {
              db = AppDatabase.instance;
              outbox = OutboxService.instance;
              sync = SyncService.instance;
              secureStorage = SecureStorageService.instance;
              await db.clearAll();
            });

            test('SecureStorage stores and retrieves authentication tokens', () async {
              await secureStorage.write('auth_access_token', 'jwt.access.test.token');
              await secureStorage.write('auth_refresh_token', 'jwt.refresh.test.token');

              final access = await secureStorage.read('auth_access_token');
              final refresh = await secureStorage.read('auth_refresh_token');

              expect(access, equals('jwt.access.test.token'));
              expect(refresh, equals('jwt.refresh.test.token'));

              await secureStorage.delete('auth_access_token');
              final deleted = await secureStorage.read('auth_access_token');
              expect(deleted, isNull);
            });

            test('Transactional outbox enqueues CREATE operation and stores optimistic cache', () async {
              const recordId = 'rec-001';
              final payload = {'name': 'Test Record', 'code': 'TR-100'};

              final op = await outbox.enqueueCreate(
                entity: '%s',
                recordId: recordId,
                payload: payload,
              );

              expect(op.id, isNotEmpty);
              expect(op.entity, equals('%s'));
              expect(op.recordId, equals(recordId));
              expect(op.action, equals('CREATE'));
              expect(op.baseVersion, equals(0));
              expect(op.status, equals('PENDING'));
              expect(op.payload?['name'], equals('Test Record'));

              final cached = await db.getCachedEntity('%s', recordId);
              expect(cached, isNotNull);
              expect(cached?['name'], equals('Test Record'));
              expect(cached?['_syncStatus'], equals('pending_create'));

              final list = await db.getCachedEntities('%s');
              expect(list.length, equals(1));
              expect(list.first['name'], equals('Test Record'));

              final pendingCount = await outbox.getPendingCount();
              expect(pendingCount, equals(1));
            });

            test('Transactional outbox enqueues UPDATE operation and updates cache optimistically', () async {
              const recordId = 'rec-002';
              final initialPayload = {'name': 'Initial', 'code': 'TR-200'};

              await outbox.enqueueCreate(
                entity: '%s',
                recordId: recordId,
                payload: initialPayload,
              );

              final updatedPayload = {'name': 'Updated', 'code': 'TR-200'};
              final updateOp = await outbox.enqueueUpdate(
                entity: '%s',
                recordId: recordId,
                payload: updatedPayload,
                baseVersion: 1,
              );

              expect(updateOp.action, equals('UPDATE'));
              expect(updateOp.baseVersion, equals(1));

              final cached = await db.getCachedEntity('%s', recordId);
              expect(cached?['name'], equals('Updated'));
              expect(cached?['_syncStatus'], equals('pending_update'));

              final pending = await outbox.getPendingOperations();
              expect(pending.length, equals(2));
              expect(pending.last.action, equals('UPDATE'));
            });

            test('Transactional outbox enqueues DELETE operation and marks cache as deleted', () async {
              const recordId = 'rec-003';
              await outbox.enqueueCreate(
                entity: '%s',
                recordId: recordId,
                payload: {'name': 'To Delete'},
              );

              await outbox.enqueueDelete(
                entity: '%s',
                recordId: recordId,
                baseVersion: 1,
              );

              final list = await db.getCachedEntities('%s');
              expect(list.isEmpty, isTrue);

              final cached = await db.getCachedEntity('%s', recordId);
              expect(cached?['_isDeleted'], isTrue);
              expect(cached?['_syncStatus'], equals('pending_delete'));
            });

            test('Conflict records are created when incompatible changes are detected without silent discarding', () async {
              final conflict = ConflictRecord(
                id: 'conf-001',
                operationId: 'op-001',
                entity: '%s',
                recordId: 'rec-100',
                baseVersion: 1,
                serverVersion: 2,
                localPayload: {'name': 'Local Name', 'status': 'ACTIVE'},
                serverPayload: {'name': 'Server Name', 'status': 'ARCHIVED'},
                conflictingFields: ['name', 'status'],
                createdAt: DateTime.now(),
                status: 'OPEN',
              );

              final database = await db.database;
              await database.insert('conflict_records', conflict.toMap());

              final openConflicts = await sync.getOpenConflicts();
              expect(openConflicts.length, equals(1));
              expect(openConflicts.first.recordId, equals('rec-100'));
              expect(openConflicts.first.conflictingFields, containsAll(['name', 'status']));
              expect(openConflicts.first.localPayload['name'], equals('Local Name'));
              expect(openConflicts.first.serverPayload['name'], equals('Server Name'));
            });

            test('Resolving conflict by discarding local updates cache with server payload', () async {
              const recordId = 'rec-200';
              const opId = 'op-200';
              const conflictId = 'conf-200';

              await db.saveCachedEntity('%s', recordId, {'name': 'Old Local'}, syncStatus: 'conflict');
              final database = await db.database;
              await database.insert('outbox_operations', {
                'id': opId,
                'entity': '%s',
                'record_id': recordId,
                'action': 'UPDATE',
                'base_version': 1,
                'created_at': DateTime.now().toIso8601String(),
                'status': 'CONFLICT',
              });

              final conflict = ConflictRecord(
                id: conflictId,
                operationId: opId,
                entity: '%s',
                recordId: recordId,
                baseVersion: 1,
                serverVersion: 3,
                localPayload: {'name': 'Local Draft'},
                serverPayload: {'name': 'Server Final'},
                conflictingFields: ['name'],
                createdAt: DateTime.now(),
                status: 'OPEN',
              );
              await database.insert('conflict_records', conflict.toMap());

              await sync.resolveConflictDiscardLocal(conflictId);

              final cached = await db.getCachedEntity('%s', recordId);
              expect(cached?['name'], equals('Server Final'));
              expect(cached?['_syncStatus'], equals('synced'));

              final openConflicts = await sync.getOpenConflicts();
              expect(openConflicts.isEmpty, isTrue);

              final pending = await outbox.getPendingOperations();
              expect(pending.where((o) => o.id == opId).isEmpty, isTrue);
            });
          });
        }
        """.formatted(
            pubName, pubName, pubName, pubName,
            sampleEntity, sampleEntity, sampleEntity, sampleEntity,
            sampleEntity, sampleEntity, sampleEntity,
            sampleEntity, sampleEntity, sampleEntity, sampleEntity,
            sampleEntity,
            sampleEntity, sampleEntity, sampleEntity, sampleEntity
        );
    }

    // =========================================================================
    // 9. HELPER METHODS
    // =========================================================================

    private static String dartType(DiagramDocument diagram, String type) {
        return switch (type) {
            case "Integer" -> "int";
            case "Long" -> "int";
            case "Decimal" -> "double";
            case "Boolean" -> "bool";
            case "Date" -> "DateTime";
            case "DateTime" -> "DateTime";
            case "UUID" -> "String";
            case "Text", "String", "Binary" -> "String";
            default -> {
                if (diagram.enumerations().stream().anyMatch(e -> e.name().equalsIgnoreCase(type))) {
                    yield dartName(type, true);
                }
                yield "String";
            }
        };
    }

    private static String dartParseField(DiagramDocument diagram, DiagramDocument.Attribute attr, String raw) {
        String type = attr.type();
        if (isEnum(diagram, type)) {
            return dartName(type, true) + ".fromString(" + raw + ")";
        }
        return switch (type) {
            case "Integer", "Long" -> "int.tryParse(" + raw + "?.toString() ?? '') ?? 0";
            case "Decimal" -> "double.tryParse(" + raw + "?.toString() ?? '') ?? 0.0";
            case "Boolean" -> raw + " == true || " + raw + "?.toString().toLowerCase() == 'true'";
            case "Date", "DateTime" -> "DateTime.tryParse(" + raw + "?.toString() ?? '') ?? DateTime.now()";
            default -> raw + "?.toString() ?? ''";
        };
    }

    private static String dartSerializeValue(DiagramDocument diagram, DiagramDocument.Attribute attr, String field) {
        String type = attr.type();
        if (isEnum(diagram, type)) {
            return field + ".toJson()";
        }
        return switch (type) {
            case "Date" -> field + ".toIso8601String().split('T').first";
            case "DateTime" -> field + ".toIso8601String()";
            default -> field;
        };
    }

    private static boolean isEnum(DiagramDocument diagram, String type) {
        return diagram.enumerations().stream().anyMatch(e -> e.name().equalsIgnoreCase(type));
    }

    private static Optional<DiagramDocument.Attribute> findPk(DiagramDocument diagram, DiagramDocument.ClassElement item) {
        var pk = item.attributes().stream().filter(DiagramDocument.Attribute::primaryKey).findFirst();
        if (pk.isPresent()) return pk;
        for (var gen : diagram.generalizations()) {
            if (gen.childId().equals(item.id())) {
                var parent = findClass(diagram, gen.parentId());
                return findPk(diagram, parent);
            }
        }
        return Optional.empty();
    }

    private static String getPkFieldName(DiagramDocument diagram, DiagramDocument.ClassElement item) {
        var pk = findPk(diagram, item);
        return pk.map(attribute -> dartName(attribute.name(), false)).orElse("id");
    }

    private static UUID findParentId(DiagramDocument diagram, UUID childId) {
        if (diagram.generalizations() == null) return null;
        for (var gen : diagram.generalizations()) {
            if (gen.childId().equals(childId)) return gen.parentId();
        }
        return null;
    }

    private static DiagramDocument.ClassElement findClass(DiagramDocument diagram, UUID id) {
        return diagram.classes().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    private static boolean isOneToOne(String sourceCard, String targetCard) {
        return (sourceCard.equals("1") || sourceCard.equals("0..1")) && (targetCard.equals("1") || targetCard.equals("0..1"));
    }

    private static boolean isManyToOne(String sourceCard, String targetCard) {
        return (sourceCard.contains("*") || sourceCard.contains("N")) && (targetCard.equals("1") || targetCard.equals("0..1"));
    }

    private static boolean isOneToMany(String sourceCard, String targetCard) {
        return (sourceCard.equals("1") || sourceCard.equals("0..1")) && (targetCard.contains("*") || targetCard.contains("N"));
    }

    private static boolean isManyToMany(String sourceCard, String targetCard) {
        return (sourceCard.contains("*") || sourceCard.contains("N")) && (targetCard.contains("*") || targetCard.contains("N"));
    }

    private static String dartName(String value, boolean capitalize) {
        String clean = value.replaceAll("[^A-Za-z0-9_]", "_");
        if (clean.isEmpty()) return "item";
        return capitalize
            ? Character.toUpperCase(clean.charAt(0)) + clean.substring(1)
            : Character.toLowerCase(clean.charAt(0)) + clean.substring(1);
    }

    private static String sqlName(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("[^A-Za-z0-9]", "_").toLowerCase(Locale.ROOT);
    }

    private static String sanitizeZipPath(String path) {
        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.contains("..")) {
            throw new IllegalArgumentException("Ruta no permitida en archivo ZIP: " + path);
        }
        return normalized;
    }
}
