import 'package:flutter/material.dart';
import 'package:ventas/core/widgets/confirm_dialog.dart';
import 'package:ventas/data/models/pedido.dart';
import 'package:ventas/data/repositories/pedido_repository.dart';
import 'package:ventas/presentation/screens/pedido/pedido_form_screen.dart';

class PedidoDetailScreen extends StatefulWidget {
  final Pedido item;
  const PedidoDetailScreen({super.key, required this.item});

  @override
  State<PedidoDetailScreen> createState() => _PedidoDetailScreenState();
}

class _PedidoDetailScreenState extends State<PedidoDetailScreen> {
  late Pedido item;
  final PedidoRepository _repo = PedidoRepository();

  @override
  void initState() {
    super.initState();
    item = widget.item;
  }

  Widget _buildRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 140,
            child: Text(label, style: const TextStyle(fontWeight: FontWeight.bold, color: Colors.grey)),
          ),
          Expanded(
            child: Text(value, style: const TextStyle(fontSize: 15)),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Detalle de ${item.displayLabel}'),
        actions: [
          IconButton(
            icon: const Icon(Icons.edit),
            onPressed: () async {
              final updated = await Navigator.of(context).push<Pedido>(
                MaterialPageRoute(builder: (_) => PedidoFormScreen(item: item)),
              );
              if (updated != null) {
                setState(() => item = updated);
              }
            },
          ),
          IconButton(
            icon: const Icon(Icons.delete),
            onPressed: () async {
              final confirm = await ConfirmDialog.show(context);
              if (confirm && mounted) {
                await _repo.delete(item.id);
                Navigator.of(context).pop();
              }
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(18.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Icon(Icons.info_outline, color: Color(0xFF1E3A8A)),
                      const SizedBox(width: 8),
                      Text(
                        item.displayLabel,
                        style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  const Divider(height: 24),
                  _buildRow('id', item.id.toString()),
                  _buildRow('numero', item.numero),
                  _buildRow('total', item.total.toString()),
                  _buildRow('estado', item.estado.toString()),
                  _buildRow('Relación Cliente ID', item.clienteId ?? '-'),
                  _buildRow('Relación Factura ID', item.facturaId ?? '-'),

                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
