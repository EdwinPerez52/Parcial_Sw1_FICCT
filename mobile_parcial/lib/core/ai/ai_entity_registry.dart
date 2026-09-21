import 'package:ventas/core/ai/ai_models.dart';
import 'package:ventas/data/models/cliente.dart';
import 'package:ventas/data/models/pedido.dart';
import 'package:ventas/data/models/factura.dart';
import 'package:ventas/data/models/producto.dart';
import 'package:ventas/data/models/estado_pedido.dart';
import 'package:ventas/data/repositories/cliente_repository.dart';
import 'package:ventas/data/repositories/pedido_repository.dart';
import 'package:ventas/data/repositories/factura_repository.dart';
import 'package:ventas/data/repositories/producto_repository.dart';

class AiAttributeMeta {
  final String name;
  final String type;
  final bool required;
  final bool isPrimaryKey;
  final List<String>? enumValues;
  final List<String> aliases;

  const AiAttributeMeta({
    required this.name,
    required this.type,
    this.required = false,
    this.isPrimaryKey = false,
    this.enumValues,
    this.aliases = const [],
  });

  String? validate(dynamic value) {
    if (value == null || value.toString().trim().isEmpty) {
      if (required && !isPrimaryKey) {
        return 'El campo "$name" es requerido';
      }
      return null;
    }

    final str = value.toString().trim();
    switch (type.toLowerCase()) {
      case 'integer':
      case 'int':
      case 'long':
        if (int.tryParse(str) == null) {
          return 'El campo "$name" debe ser un número entero válido';
        }
        break;
      case 'decimal':
      case 'double':
      case 'float':
      case 'number':
        if (double.tryParse(str) == null) {
          return 'El campo "$name" debe ser un número decimal válido';
        }
        break;
      case 'boolean':
      case 'bool':
        final lower = str.toLowerCase();
        if (lower != 'true' && lower != 'false' && lower != '1' && lower != '0' && lower != 'si' && lower != 'no') {
          return 'El campo "$name" debe ser un booleano (true/false, si/no)';
        }
        break;
      default:
        if (enumValues != null && enumValues!.isNotEmpty) {
          final upper = str.toUpperCase();
          if (!enumValues!.contains(upper)) {
            return 'El campo "$name" debe ser uno de: ${enumValues!.join(', ')}';
          }
        }
    }
    return null;
  }
}

class AiEntityMeta {
  final String name;
  final String tableName;
  final String plural;
  final List<AiAttributeMeta> attributes;
  final List<String> aliases;
  final Future<dynamic> Function(Map<String, dynamic> data)? onCreate;
  final Future<dynamic> Function(String id, Map<String, dynamic> data)? onUpdate;
  final Future<void> Function(String id)? onDelete;
  final Future<List<dynamic>> Function(String? search)? onSearch;

  const AiEntityMeta({
    required this.name,
    required this.tableName,
    required this.plural,
    required this.attributes,
    this.aliases = const [],
    this.onCreate,
    this.onUpdate,
    this.onDelete,
    this.onSearch,
  });

  List<String> validatePayload(Map<String, dynamic> payload, {bool isCreate = true}) {
    final errors = <String>[];
    for (final attr in attributes) {
      if (attr.isPrimaryKey) continue;
      final val = payload[attr.name];
      if (isCreate && attr.required && (val == null || val.toString().trim().isEmpty)) {
        errors.add('El campo "${attr.name}" es obligatorio para crear $name');
      } else if (val != null && val.toString().trim().isNotEmpty) {
        final err = attr.validate(val);
        if (err != null) errors.add(err);
      }
    }
    return errors;
  }

  Future<dynamic> execute(AiCrudProposal proposal) async {
    switch (proposal.action) {
      case AiCrudAction.create:
        if (onCreate != null) {
          return await onCreate!(proposal.payload);
        }
        throw UnsupportedError('Operación de creación no soportada para $name');
      case AiCrudAction.update:
        if (proposal.recordId == null || proposal.recordId!.trim().isEmpty) {
          throw ArgumentError('Se requiere recordId para actualizar $name');
        }
        if (onUpdate != null) {
          return await onUpdate!(proposal.recordId!, proposal.payload);
        }
        throw UnsupportedError('Operación de actualización no soportada para $name');
      case AiCrudAction.delete:
        if (proposal.recordId == null || proposal.recordId!.trim().isEmpty) {
          throw ArgumentError('Se requiere recordId para eliminar $name');
        }
        if (onDelete != null) {
          await onDelete!(proposal.recordId!);
          return true;
        }
        throw UnsupportedError('Operación de eliminación no soportada para $name');
      case AiCrudAction.search:
        if (onSearch != null) {
          final query = proposal.recordId ?? proposal.payload.values.firstOrNull?.toString();
          return await onSearch!(query);
        }
        return [];
    }
  }
}

class AiEntityRegistry {
  static final AiEntityRegistry instance = AiEntityRegistry._();
  final Map<String, AiEntityMeta> _entities = {};

  AiEntityRegistry._() {
    _registerDefaultVentasEntities();
  }

  void registerEntity(AiEntityMeta meta) {
    _entities[meta.name.toLowerCase()] = meta;
  }

  List<AiEntityMeta> get allEntities => _entities.values.toList();

  AiEntityMeta? findEntity(String rawName) {
    final clean = rawName.trim().toLowerCase();
    if (_entities.containsKey(clean)) return _entities[clean];

    for (final meta in _entities.values) {
      if (meta.name.toLowerCase() == clean ||
          meta.tableName.toLowerCase() == clean ||
          meta.plural.toLowerCase() == clean ||
          meta.aliases.any((a) => a.toLowerCase() == clean)) {
        return meta;
      }
    }
    return null;
  }

  void _registerDefaultVentasEntities() {
    // 1. Cliente
    final clienteRepo = ClienteRepository();
    registerEntity(
      AiEntityMeta(
        name: 'Cliente',
        tableName: 'cliente',
        plural: 'clientes',
        aliases: ['usuario', 'comprador', 'consumidor'],
        attributes: const [
          AiAttributeMeta(name: 'id', type: 'UUID', isPrimaryKey: true),
          AiAttributeMeta(name: 'nombre', type: 'String', required: true, aliases: ['name', 'contacto']),
          AiAttributeMeta(name: 'email', type: 'String', required: true, aliases: ['correo', 'mail']),
        ],
        onCreate: (data) async => await clienteRepo.create(Cliente.fromJson(data)),
        onUpdate: (id, data) async => await clienteRepo.update(id, Cliente.fromJson(data)),
        onDelete: (id) async => await clienteRepo.delete(id),
        onSearch: (q) async => await clienteRepo.getAll(search: q),
      ),
    );

    // 2. Factura
    final facturaRepo = FacturaRepository();
    registerEntity(
      AiEntityMeta(
        name: 'Factura',
        tableName: 'factura',
        plural: 'facturas',
        aliases: ['recibo', 'comprobante', 'boleta', 'ticket', 'invoice'],
        attributes: const [
          AiAttributeMeta(name: 'id', type: 'UUID', isPrimaryKey: true),
          AiAttributeMeta(
            name: 'numeroFactura',
            type: 'String',
            required: true,
            aliases: ['numero', 'folio', 'codigo', 'nro'],
          ),
          AiAttributeMeta(
            name: 'monto',
            type: 'Decimal',
            required: true,
            aliases: ['total', 'importe', 'precio', 'valor', 'subtotal'],
          ),
        ],
        onCreate: (data) async => await facturaRepo.create(Factura.fromJson(data)),
        onUpdate: (id, data) async => await facturaRepo.update(id, Factura.fromJson(data)),
        onDelete: (id) async => await facturaRepo.delete(id),
        onSearch: (q) async => await facturaRepo.getAll(search: q),
      ),
    );

    // 3. Producto
    final productoRepo = ProductoRepository();
    registerEntity(
      AiEntityMeta(
        name: 'Producto',
        tableName: 'producto',
        plural: 'productos',
        aliases: ['articulo', 'item', 'mercaderia'],
        attributes: const [
          AiAttributeMeta(name: 'id', type: 'UUID', isPrimaryKey: true),
          AiAttributeMeta(name: 'codigo', type: 'String', required: true, aliases: ['sku', 'ref', 'code']),
          AiAttributeMeta(name: 'precio', type: 'Decimal', required: true, aliases: ['costo', 'valor', 'monto']),
          AiAttributeMeta(name: 'stock', type: 'Integer', required: true, aliases: ['cantidad', 'existencia']),
        ],
        onCreate: (data) async => await productoRepo.create(Producto.fromJson(data)),
        onUpdate: (id, data) async => await productoRepo.update(id, Producto.fromJson(data)),
        onDelete: (id) async => await productoRepo.delete(id),
        onSearch: (q) async => await productoRepo.getAll(search: q),
      ),
    );

    // 4. Pedido
    final pedidoRepo = PedidoRepository();
    registerEntity(
      AiEntityMeta(
        name: 'Pedido',
        tableName: 'pedido',
        plural: 'pedidos',
        aliases: ['orden', 'solicitud', 'order'],
        attributes: [
          const AiAttributeMeta(name: 'id', type: 'UUID', isPrimaryKey: true),
          const AiAttributeMeta(name: 'numero', type: 'String', required: true, aliases: ['nro', 'codigo']),
          const AiAttributeMeta(name: 'total', type: 'Decimal', required: true, aliases: ['monto', 'precio']),
          AiAttributeMeta(
            name: 'estado',
            type: 'EstadoPedido',
            required: true,
            enumValues: EstadoPedido.values.map((e) => e.name).toList(),
            aliases: ['status'],
          ),
          const AiAttributeMeta(name: 'clienteId', type: 'String', required: false, aliases: ['cliente']),
          const AiAttributeMeta(name: 'facturaId', type: 'String', required: false, aliases: ['factura']),
          const AiAttributeMeta(name: 'productoIds', type: 'List<String>', required: false, aliases: ['productos']),
        ],
        onCreate: (data) async => await pedidoRepo.create(Pedido.fromJson(data)),
        onUpdate: (id, data) async => await pedidoRepo.update(id, Pedido.fromJson(data)),
        onDelete: (id) async => await pedidoRepo.delete(id),
        onSearch: (q) async => await pedidoRepo.getAll(search: q),
      ),
    );
  }
}
