import 'dart:convert';
import 'package:sqflite/sqflite.dart';
import 'package:uuid/uuid.dart';
import 'package:ventas/core/database/app_database.dart';

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
