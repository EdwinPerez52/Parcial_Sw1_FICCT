import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:ventas/core/database/app_database.dart';
import 'package:ventas/core/storage/secure_storage_service.dart';
import 'package:ventas/core/sync/outbox_service.dart';
import 'package:ventas/core/sync/sync_service.dart';

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
        entity: 'cliente',
        recordId: recordId,
        payload: payload,
      );

      expect(op.id, isNotEmpty);
      expect(op.entity, equals('cliente'));
      expect(op.recordId, equals(recordId));
      expect(op.action, equals('CREATE'));
      expect(op.baseVersion, equals(0));
      expect(op.status, equals('PENDING'));
      expect(op.payload?['name'], equals('Test Record'));

      final cached = await db.getCachedEntity('cliente', recordId);
      expect(cached, isNotNull);
      expect(cached?['name'], equals('Test Record'));
      expect(cached?['_syncStatus'], equals('pending_create'));

      final list = await db.getCachedEntities('cliente');
      expect(list.length, equals(1));
      expect(list.first['name'], equals('Test Record'));

      final pendingCount = await outbox.getPendingCount();
      expect(pendingCount, equals(1));
    });

    test('Transactional outbox enqueues UPDATE operation and updates cache optimistically', () async {
      const recordId = 'rec-002';
      final initialPayload = {'name': 'Initial', 'code': 'TR-200'};

      await outbox.enqueueCreate(
        entity: 'cliente',
        recordId: recordId,
        payload: initialPayload,
      );

      final updatedPayload = {'name': 'Updated', 'code': 'TR-200'};
      final updateOp = await outbox.enqueueUpdate(
        entity: 'cliente',
        recordId: recordId,
        payload: updatedPayload,
        baseVersion: 1,
      );

      expect(updateOp.action, equals('UPDATE'));
      expect(updateOp.baseVersion, equals(1));

      final cached = await db.getCachedEntity('cliente', recordId);
      expect(cached?['name'], equals('Updated'));
      expect(cached?['_syncStatus'], equals('pending_update'));

      final pending = await outbox.getPendingOperations();
      expect(pending.length, equals(2));
      expect(pending.last.action, equals('UPDATE'));
    });

    test('Transactional outbox enqueues DELETE operation and marks cache as deleted', () async {
      const recordId = 'rec-003';
      await outbox.enqueueCreate(
        entity: 'cliente',
        recordId: recordId,
        payload: {'name': 'To Delete'},
      );

      await outbox.enqueueDelete(
        entity: 'cliente',
        recordId: recordId,
        baseVersion: 1,
      );

      final list = await db.getCachedEntities('cliente');
      expect(list.isEmpty, isTrue);

      final cached = await db.getCachedEntity('cliente', recordId);
      expect(cached?['_isDeleted'], isTrue);
      expect(cached?['_syncStatus'], equals('pending_delete'));
    });

    test('Conflict records are created when incompatible changes are detected without silent discarding', () async {
      final conflict = ConflictRecord(
        id: 'conf-001',
        operationId: 'op-001',
        entity: 'cliente',
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

      await db.saveCachedEntity('cliente', recordId, {'name': 'Old Local'}, syncStatus: 'conflict');
      final database = await db.database;
      await database.insert('outbox_operations', {
        'id': opId,
        'entity': 'cliente',
        'record_id': recordId,
        'action': 'UPDATE',
        'base_version': 1,
        'created_at': DateTime.now().toIso8601String(),
        'status': 'CONFLICT',
      });

      final conflict = ConflictRecord(
        id: conflictId,
        operationId: opId,
        entity: 'cliente',
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

      final cached = await db.getCachedEntity('cliente', recordId);
      expect(cached?['name'], equals('Server Final'));
      expect(cached?['_syncStatus'], equals('synced'));

      final openConflicts = await sync.getOpenConflicts();
      expect(openConflicts.isEmpty, isTrue);

      final pending = await outbox.getPendingOperations();
      expect(pending.where((o) => o.id == opId).isEmpty, isTrue);
    });
  });
}
