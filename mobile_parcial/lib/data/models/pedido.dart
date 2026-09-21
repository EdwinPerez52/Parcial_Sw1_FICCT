// Modelo tipado para Pedido

import 'package:ventas/data/models/estado_pedido.dart';

class Pedido {
  final String id;
  final String numero;
  final double total;
  final EstadoPedido estado;
  final String? clienteId;
  final String? facturaId;
  final List<String> productoIds;

  Pedido({
    required this.id,
    required this.numero,
    required this.total,
    required this.estado,
    this.clienteId,
    this.facturaId,
    this.productoIds = const [],
  });

  factory Pedido.fromJson(Map<String, dynamic> json) {
    return Pedido(
      id: json['id']?.toString() ?? '',
      numero: json['numero']?.toString() ?? '',
      total: double.tryParse(json['total']?.toString() ?? '') ?? 0.0,
      estado: EstadoPedido.fromString(json['estado']),
      clienteId: json['clienteId']?.toString(),
      facturaId: json['facturaId']?.toString(),
      productoIds: (json['productoIds'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? [],
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'numero': numero,
    'total': total,
    'estado': estado.toJson(),
    'clienteId': clienteId,
    'facturaId': facturaId,
    'productoIds': productoIds,
  };

  Map<String, dynamic> toInputJson() {
    final map = toJson();
    map.remove('id');
    map.remove('id');
    return map;
  }

  String get displayLabel {
    return numero;
  }
}
