import 'package:uuid/uuid.dart';
import 'package:ventas/core/api/api_client.dart';
import 'package:ventas/core/database/app_database.dart';
import 'package:ventas/core/sync/outbox_service.dart';
import 'package:ventas/core/sync/sync_service.dart';
import 'package:ventas/data/models/pedido.dart';

class PedidoRepository {
  final ApiClient _client = ApiClient.instance;
  final AppDatabase _db = AppDatabase.instance;
  final OutboxService _outbox = OutboxService.instance;
  final SyncService _sync = SyncService.instance;
  static const String _entity = 'pedido';
  static const Uuid _uuid = Uuid();

  Future<List<Pedido>> getAll({int page = 0, int size = 20, String? search}) async {
    if (_sync.isOnline) {
      _sync.syncDown().catchError((_) {});
    }

    final localRows = await _db.getCachedEntities(_entity);
    List<Pedido> items = localRows.map((e) => Pedido.fromJson(e)).toList();

    if (items.isEmpty && _sync.isOnline) {
      try {
        final query = <String, String>{};
        if (search != null && search.trim().isNotEmpty) {
          query['search'] = search.trim();
        }
        final data = await _client.get('/api/pedido', queryParams: query);
        if (data is List) {
          for (final item in data) {
            if (item is Map<String, dynamic>) {
              final id = item['id']?.toString() ?? _uuid.v4();
              await _db.saveCachedEntity(_entity, id, item, syncStatus: 'synced');
            }
          }
          final refreshed = await _db.getCachedEntities(_entity);
          items = refreshed.map((e) => Pedido.fromJson(e)).toList();
        }
      } catch (_) {}
    }

    if (search != null && search.trim().isNotEmpty) {
      final s = search.trim().toLowerCase();
      items = items.where((i) => i.displayLabel.toLowerCase().contains(s)).toList();
    }
    return items;
  }

  Future<Pedido> getById(String id) async {
    final local = await _db.getCachedEntity(_entity, id);
    if (local != null) {
      return Pedido.fromJson(local);
    }
    final data = await _client.get('/api/pedido/$id') as Map<String, dynamic>;
    final item = Pedido.fromJson(data);
    await _db.saveCachedEntity(_entity, id, data, syncStatus: 'synced');
    return item;
  }

  Future<Pedido> create(Pedido item) async {
    final id = item.id.isNotEmpty ? item.id : _uuid.v4();
    final json = item.toJson()..['id'] = id;
    final createdItem = Pedido.fromJson(json);

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

  Future<Pedido> update(String id, Pedido item) async {
    final cached = await _db.getCachedEntity(_entity, id);
    final baseVersion = (cached?['_version'] as int?) ?? 1;

    final json = item.toJson()..['id'] = id;
    final updatedItem = Pedido.fromJson(json);

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
