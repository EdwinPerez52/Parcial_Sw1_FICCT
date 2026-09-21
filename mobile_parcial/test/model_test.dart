import 'package:flutter_test/flutter_test.dart';
import 'package:ventas/data/models/cliente.dart';
import 'package:ventas/data/models/pedido.dart';
import 'package:ventas/data/models/factura.dart';
import 'package:ventas/data/models/producto.dart';


void main() {
  group('Domain Models Unit Tests', () {
test('Cliente serializes and deserializes JSON correctly', () {
  final json = <String, dynamic>{
    'id': 'test-uuid-1',
  };
  final instance = Cliente.fromJson(json);
  expect(instance.id, 'test-uuid-1');
  expect(instance.toJson()['id'], 'test-uuid-1');
});
test('Pedido serializes and deserializes JSON correctly', () {
  final json = <String, dynamic>{
    'id': 'test-uuid-1',
  };
  final instance = Pedido.fromJson(json);
  expect(instance.id, 'test-uuid-1');
  expect(instance.toJson()['id'], 'test-uuid-1');
});
test('Factura serializes and deserializes JSON correctly', () {
  final json = <String, dynamic>{
    'id': 'test-uuid-1',
  };
  final instance = Factura.fromJson(json);
  expect(instance.id, 'test-uuid-1');
  expect(instance.toJson()['id'], 'test-uuid-1');
});
test('Producto serializes and deserializes JSON correctly', () {
  final json = <String, dynamic>{
    'id': 'test-uuid-1',
  };
  final instance = Producto.fromJson(json);
  expect(instance.id, 'test-uuid-1');
  expect(instance.toJson()['id'], 'test-uuid-1');
});

  });
}
