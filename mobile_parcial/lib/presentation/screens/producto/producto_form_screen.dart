import 'package:flutter/material.dart';
import 'package:ventas/core/i18n/app_strings.dart';
import 'package:ventas/data/models/producto.dart';
import 'package:ventas/data/repositories/producto_repository.dart';

class ProductoFormScreen extends StatefulWidget {
  final Producto? item;
  const ProductoFormScreen({super.key, this.item});

  @override
  State<ProductoFormScreen> createState() => _ProductoFormScreenState();
}

class _ProductoFormScreenState extends State<ProductoFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final ProductoRepository _repository = ProductoRepository();
  bool _saving = false;

  final _codigoController = TextEditingController();
  final _precioController = TextEditingController();
  final _stockController = TextEditingController();



  @override
  void initState() {
    super.initState();
    if (widget.item != null) _codigoController.text = widget.item!.codigo.toString();
    if (widget.item != null) _precioController.text = widget.item!.precio.toString();
    if (widget.item != null) _stockController.text = widget.item!.stock.toString();


  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      final payload = Producto(
        id: widget.item?.id ?? '',
      codigo: _codigoController.text.trim(),
      precio: double.tryParse(_precioController.text) ?? 0.0,
      stock: int.tryParse(_stockController.text) ?? 0,
      );
      Producto saved;
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
        title: Text(isEdit ? 'Editar Producto' : 'Nuevo Producto'),
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
            controller: _codigoController,
            decoration: const InputDecoration(labelText: 'codigo'),
            validator: (val) {
              if (val == null || val.trim().isEmpty) return AppStrings.fieldRequired;
              return null;
            },
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _precioController,
            decoration: const InputDecoration(labelText: 'precio'),
            keyboardType: TextInputType.number,
            validator: (val) {
              if (val == null || val.trim().isEmpty) return AppStrings.fieldRequired;
              if (num.tryParse(val) == null) return AppStrings.invalidNumber;
              return null;
            },
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _stockController,
            decoration: const InputDecoration(labelText: 'stock'),
            keyboardType: TextInputType.number,
            validator: (val) {
              if (val == null || val.trim().isEmpty) return AppStrings.fieldRequired;
              if (num.tryParse(val) == null) return AppStrings.invalidNumber;
              return null;
            },
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
