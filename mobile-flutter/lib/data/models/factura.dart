// Modelo tipado para Factura


class Factura {
  final String id;
  final String numeroFactura;
  final double monto;

  Factura({
    required this.id,
    required this.numeroFactura,
    required this.monto,
  });

  factory Factura.fromJson(Map<String, dynamic> json) {
    return Factura(
      id: json['id']?.toString() ?? '',
      numeroFactura: json['numeroFactura']?.toString() ?? '',
      monto: double.tryParse(json['monto']?.toString() ?? '') ?? 0.0,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'numeroFactura': numeroFactura,
    'monto': monto,
  };

  Map<String, dynamic> toInputJson() {
    final map = toJson();
    map.remove('id');
    map.remove('id');
    return map;
  }

  String get displayLabel {
    return numeroFactura;
  }
}
