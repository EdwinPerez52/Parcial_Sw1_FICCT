import 'package:flutter/material.dart';
import 'package:ventas/core/i18n/app_strings.dart';
import 'package:ventas/data/models/cliente.dart';
import 'package:ventas/data/repositories/cliente_repository.dart';

class ClienteFormScreen extends StatefulWidget {
  final Cliente? item;
  const ClienteFormScreen({super.key, this.item});

  @override
  State<ClienteFormScreen> createState() => _ClienteFormScreenState();
}

class _ClienteFormScreenState extends State<ClienteFormScreen> {
  final _formKey = GlobalKey<FormState>();
  final ClienteRepository _repository = ClienteRepository();
  bool _saving = false;

  final _nombreController = TextEditingController();
  final _emailController = TextEditingController();



  @override
  void initState() {
    super.initState();
    if (widget.item != null) _nombreController.text = widget.item!.nombre.toString();
    if (widget.item != null) _emailController.text = widget.item!.email.toString();


  }

  Future<void> _submit() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() => _saving = true);
    try {
      final payload = Cliente(
        id: widget.item?.id ?? '',
      nombre: _nombreController.text.trim(),
      email: _emailController.text.trim(),
      );
      Cliente saved;
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
        title: Text(isEdit ? 'Editar Cliente' : 'Nuevo Cliente'),
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
            controller: _nombreController,
            decoration: const InputDecoration(labelText: 'nombre'),
            validator: (val) {
              if (val == null || val.trim().isEmpty) return AppStrings.fieldRequired;
              return null;
            },
          ),
          const SizedBox(height: 16),
          TextFormField(
            controller: _emailController,
            decoration: const InputDecoration(labelText: 'email'),
            validator: (val) {
              if (val == null || val.trim().isEmpty) return AppStrings.fieldRequired;
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
