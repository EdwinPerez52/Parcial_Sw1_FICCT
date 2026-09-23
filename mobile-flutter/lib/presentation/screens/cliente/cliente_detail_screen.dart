import 'package:flutter/material.dart';
import 'package:ventas/core/widgets/confirm_dialog.dart';
import 'package:ventas/data/models/cliente.dart';
import 'package:ventas/data/repositories/cliente_repository.dart';
import 'package:ventas/presentation/screens/cliente/cliente_form_screen.dart';

class ClienteDetailScreen extends StatefulWidget {
  final Cliente item;
  const ClienteDetailScreen({super.key, required this.item});

  @override
  State<ClienteDetailScreen> createState() => _ClienteDetailScreenState();
}

class _ClienteDetailScreenState extends State<ClienteDetailScreen> {
  late Cliente item;
  final ClienteRepository _repo = ClienteRepository();

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
              final updated = await Navigator.of(context).push<Cliente>(
                MaterialPageRoute(builder: (_) => ClienteFormScreen(item: item)),
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
                  _buildRow('nombre', item.nombre),
                  _buildRow('email', item.email),

                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
