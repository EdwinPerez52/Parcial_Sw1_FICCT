import 'dart:convert';
import 'dart:io' show Platform;
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:path/path.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';

class AppDatabase {
  static final AppDatabase instance = AppDatabase._();
  AppDatabase._();

  static String databaseName = 'collab_modeler_offline.db';
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
    final path = join(dbPath, databaseName);

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
