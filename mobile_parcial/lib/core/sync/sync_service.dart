import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:sqflite/sqflite.dart';
import 'package:uuid/uuid.dart';
import 'package:ventas/core/api/api_client.dart';
import 'package:ventas/core/config/app_config.dart';
import 'package:ventas/core/database/app_database.dart';
import 'package:ventas/core/sync/outbox_service.dart';

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
