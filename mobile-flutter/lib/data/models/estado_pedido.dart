// ignore_for_file: constant_identifier_names

enum EstadoPedido {
  NUEVO,
  PAGADO,
  CANCELADO;

  static EstadoPedido fromString(dynamic value) {
    final str = value?.toString().toUpperCase() ?? '';
    return EstadoPedido.values.firstWhere(
      (e) => e.name.toUpperCase() == str,
      orElse: () => EstadoPedido.values.first,
    );
  }

  String toJson() => name;
  String get displayName => name;
}
