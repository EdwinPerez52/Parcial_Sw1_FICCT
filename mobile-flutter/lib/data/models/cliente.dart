// Modelo tipado para Cliente


class Cliente {
  final String id;
  final String nombre;
  final String email;

  Cliente({
    required this.id,
    required this.nombre,
    required this.email,
  });

  factory Cliente.fromJson(Map<String, dynamic> json) {
    return Cliente(
      id: json['id']?.toString() ?? '',
      nombre: json['nombre']?.toString() ?? '',
      email: json['email']?.toString() ?? '',
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'nombre': nombre,
    'email': email,
  };

  Map<String, dynamic> toInputJson() {
    final map = toJson();
    map.remove('id');
    map.remove('id');
    return map;
  }

  String get displayLabel {
    return nombre;
  }
}
