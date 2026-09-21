import 'package:flutter/material.dart';
import 'package:ventas/core/i18n/app_strings.dart';
import 'package:ventas/core/widgets/relation_picker.dart';
import 'package:ventas/data/models/pedido.dart';
import 'package:ventas/data/repositories/pedido_repository.dart';
import 'package:ventas/data/models/estado_pedido.dart';
import 'package:ventas/data/models/cliente.dart';
import 'package:ventas/data/repositories/cliente_repository.dart';
import 'package:ventas/data/models/factura.dart';
import 'package:ventas/data/repositories/factura_repository.dart';

class PedidoFormScreen extends StatefulWidget {
  final Pedido? item;
  const PedidoFormScreen({super.key, this.item});

  @override
  State<PedidoFormScreen> createState() => _PedidoFormScreenState();
}

class _PedidoFormScreenState extends State<PedidoFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final PedidoRepository _repository = PedidoRepository();
  bool _saving = false;

  final _numeroController = TextEditingController();
  final _totalController = TextEditingController();
  EstadoPedido? _estado;
  String? _clienteId;
  String? _facturaId;

  List<Cliente> _clienteOptions = [];
  List<Factura> _facturaOptions = [];


  @override
  void initState() {
    super.initState();
    if (widget.item != null) _numeroController.text = widget.item!.numero.toString();
    if (widget.item != null) _totalController.text = widget.item!.total.toString();
    _estado = widget.item?.estado ?? EstadoPedido.values.first;
    _clienteId = widget.item?.clienteId;
    _facturaId = widget.item?.facturaId;

    ClienteRepository().getAll().then((list) {
      if (mounted) setState(() => _clienteOptions = list);
    });
    FacturaRepository().getAll().then((list) {
      if (mounted) setState(() => _facturaOptions = list);
    });

  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      final payload = Pedido(
        id: widget.item?.id ?? '',
      numero: _numeroController.text.trim(),
      total: double.tryParse(_totalController.text) ?? 0.0,
      estado: _estado ?? EstadoPedido.values.first,
      clienteId: _clienteId,
      facturaId: _facturaId,
      );
      Pedido saved;
      if (widget.item != null) {
        saved = await _repository.update(widget.item!.id, payload);
      } else {
        saved = await _repository.create(payload);
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text(AppStrings.savedSuccess)),
        );
        Navigator.of(context).pop(saved);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(e.toString()), backgroundColor: Colors.red),
        );
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isEdit = widget.item != null;
    return Scaffold(
      appBar: AppBar(
        title: Text(isEdit ? 'Editar Pedido' : 'Nuevo Pedido'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(20.0),
            child: Form(
              key: _formKey,
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
          TextFormField(
            controller: _numeroController,
            decoration: const InputDecoration(labelText: 'numero'),
            validator: (val) {
              if (val == null || val.trim().isEmpty) return AppStrings.fieldRequired;
              return null;
            },
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _totalController,
            decoration: const InputDecoration(labelText: 'total'),
            keyboardType: TextInputType.number,
            validator: (val) {
              if (val == null || val.trim().isEmpty) return AppStrings.fieldRequired;
              if (num.tryParse(val) == null) return AppStrings.invalidNumber;
              return null;
            },
          ),
          const SizedBox(height: 16),
          DropdownButtonFormField<EstadoPedido>(
            initialValue: _estado,
            decoration: const InputDecoration(labelText: 'estado'),
            items: EstadoPedido.values.map((e) => DropdownMenuItem(value: e, child: Text(e.displayName))).toList(),
            onChanged: (val) => setState(() => _estado = val),
          ),
          const SizedBox(height: 16),
          RelationDropdown<Cliente>(
            label: 'Seleccionar Cliente',
            value: _clienteOptions.where((e) => e.id == _clienteId).firstOrNull,
            items: _clienteOptions,
            itemLabel: (e) => e.displayLabel,
            itemValue: (e) => e.id,
            onChanged: (val) => setState(() => _clienteId = val?.id),
          ),
          const SizedBox(height: 16),
          RelationDropdown<Factura>(
            label: 'Seleccionar Factura',
            value: _facturaOptions.where((e) => e.id == _facturaId).firstOrNull,
            items: _facturaOptions,
            itemLabel: (e) => e.displayLabel,
            itemValue: (e) => e.id,
            onChanged: (val) => setState(() => _facturaId = val?.id),
          ),
          const SizedBox(height: 16),

                  const SizedBox(height: 12),
                  ElevatedButton(
                    onPressed: _saving ? null : _submit,
                    child: _saving
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                          )
                        : const Text(AppStrings.save),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
