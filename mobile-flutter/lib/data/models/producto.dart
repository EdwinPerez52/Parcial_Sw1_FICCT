// Modelo tipado para Producto


class Producto {
  final String id;
  final String codigo;
  final double precio;
  final int stock;

  Producto({
    required this.id,
    required this.codigo,
    required this.precio,
    required this.stock,
  });

  factory Producto.fromJson(Map<String, dynamic> json) {
    return Producto(
      id: json['id']?.toString() ?? '',
      codigo: json['codigo']?.toString() ?? '',
      precio: double.tryParse(json['precio']?.toString() ?? '') ?? 0.0,
      stock: int.tryParse(json['stock']?.toString() ?? '') ?? 0,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'codigo': codigo,
    'precio': precio,
    'stock': stock,
  };

  Map<String, dynamic> toInputJson() {
    final map = toJson();
    map.remove('id');
    map.remove('id');
    return map;
  }

  String get displayLabel {
    return codigo;
  }
}
