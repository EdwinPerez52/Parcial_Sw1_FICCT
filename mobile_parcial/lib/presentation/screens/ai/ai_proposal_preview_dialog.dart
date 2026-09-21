import 'package:flutter/material.dart';
import 'package:ventas/core/ai/ai_entity_registry.dart';
import 'package:ventas/core/ai/ai_models.dart';
import 'package:ventas/presentation/screens/cliente/cliente_form_screen.dart';
import 'package:ventas/presentation/screens/factura/factura_form_screen.dart';
import 'package:ventas/presentation/screens/pedido/pedido_form_screen.dart';
import 'package:ventas/presentation/screens/producto/producto_form_screen.dart';
import 'package:ventas/data/models/cliente.dart';
import 'package:ventas/data/models/factura.dart';
import 'package:ventas/data/models/pedido.dart';
import 'package:ventas/data/models/producto.dart';

class AiProposalPreviewDialog extends StatefulWidget {
  final AiCrudProposal proposal;

  const AiProposalPreviewDialog({super.key, required this.proposal});

  static Future<bool?> show(BuildContext context, AiCrudProposal proposal) {
    return showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (_) => AiProposalPreviewDialog(proposal: proposal),
    );
  }

  @override
  State<AiProposalPreviewDialog> createState() => _AiProposalPreviewDialogState();
}

class _AiProposalPreviewDialogState extends State<AiProposalPreviewDialog> {
  bool _applying = false;
  String? _executionError;

  Color _actionColor(AiCrudAction action) {
    switch (action) {
      case AiCrudAction.create:
        return const Color(0xFF10B981); // Emerald green
      case AiCrudAction.update:
        return const Color(0xFFF59E0B); // Amber
      case AiCrudAction.delete:
        return const Color(0xFFEF4444); // Red
      case AiCrudAction.search:
        return const Color(0xFF3B82F6); // Blue
    }
  }

  Future<void> _applyProposal() async {
    final entityMeta = AiEntityRegistry.instance.findEntity(widget.proposal.entityType);
    if (entityMeta == null) {
      setState(() => _executionError = 'Entidad no reconocida en el sistema');
      return;
    }

    setState(() {
      _applying = true;
      _executionError = null;
    });

    try {
      await entityMeta.execute(widget.proposal);
      if (mounted) {
        Navigator.of(context).pop(true);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Row(
              children: [
                const Icon(Icons.check_circle, color: Colors.white),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Operación confirmada: ${widget.proposal.action.label} ${widget.proposal.entityType} guardada en almacenamiento local.',
                  ),
                ),
              ],
            ),
            backgroundColor: Colors.green.shade800,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _applying = false;
          _executionError = 'Error al ejecutar: ${e.toString()}';
        });
      }
    }
  }

  void _openInForm() {
    Navigator.of(context).pop(false);
    final entity = widget.proposal.entityType.toLowerCase();
    final payload = widget.proposal.payload;

    Widget? formScreen;
    if (entity == 'cliente') {
      formScreen = ClienteFormScreen(
        item: Cliente.fromJson(payload),
      );
    } else if (entity == 'factura') {
      formScreen = FacturaFormScreen(
        item: Factura.fromJson(payload),
      );
    } else if (entity == 'producto') {
      formScreen = ProductoFormScreen(
        item: Producto.fromJson(payload),
      );
    } else if (entity == 'pedido') {
      formScreen = PedidoFormScreen(
        item: Pedido.fromJson(payload),
      );
    }

    if (formScreen != null) {
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => formScreen!));
    }
  }

  @override
  Widget build(BuildContext context) {
    final proposal = widget.proposal;
    final color = _actionColor(proposal.action);

    return Container(
      decoration: const BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
      ),
      padding: EdgeInsets.only(
        top: 20,
        left: 20,
        right: 20,
        bottom: MediaQuery.of(context).viewInsets.bottom + 24,
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Drag handle
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.grey.shade300,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Header Row
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: color.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: color, width: 1.5),
                  ),
                  child: Text(
                    proposal.action.label,
                    style: TextStyle(
                      color: color,
                      fontWeight: FontWeight.bold,
                      fontSize: 13,
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(
                    proposal.entityType.isNotEmpty ? proposal.entityType : 'Operación General',
                    style: const TextStyle(
                      fontSize: 18,
                      fontWeight: FontWeight.bold,
                      color: Color(0xFF1E3A8A),
                    ),
                  ),
                ),
                Chip(
                  label: Text(
                    proposal.source.label,
                    style: const TextStyle(fontSize: 11),
                  ),
                  backgroundColor: Colors.grey.shade100,
                  visualDensity: VisualDensity.compact,
                ),
              ],
            ),

            if (proposal.recordId != null) ...[
              const SizedBox(height: 6),
              Text(
                'Registro ID: ${proposal.recordId}',
                style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
              ),
            ],

            const Divider(height: 24),

            // Validation warnings or errors
            if (proposal.validationErrors.isNotEmpty)
              Container(
                margin: const EdgeInsets.only(bottom: 12),
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.red.shade50,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.red.shade200),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Icon(Icons.error_outline, size: 18, color: Colors.red.shade700),
                        const SizedBox(width: 6),
                        Text(
                          'Validación requerida:',
                          style: TextStyle(
                            fontWeight: FontWeight.bold,
                            color: Colors.red.shade900,
                            fontSize: 13,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 4),
                    ...proposal.validationErrors.map(
                      (err) => Text('• $err', style: TextStyle(color: Colors.red.shade800, fontSize: 12)),
                    ),
                  ],
                ),
              ),

            // Fields Table
            if (proposal.payload.isNotEmpty) ...[
              const Text(
                'Campos extraídos y valores propuestos:',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
              ),
              const SizedBox(height: 8),
              Container(
                decoration: BoxDecoration(
                  color: Colors.grey.shade50,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Colors.grey.shade200),
                ),
                child: Column(
                  children: proposal.payload.entries.map((entry) {
                    return Container(
                      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                      decoration: BoxDecoration(
                        border: Border(bottom: BorderSide(color: Colors.grey.shade200, width: 0.5)),
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            flex: 2,
                            child: Text(
                              entry.key,
                              style: TextStyle(
                                fontWeight: FontWeight.w600,
                                fontSize: 13,
                                color: Colors.grey.shade800,
                              ),
                            ),
                          ),
                          Expanded(
                            flex: 3,
                            child: Text(
                              entry.value?.toString() ?? '',
                              style: const TextStyle(fontSize: 13, color: Colors.black87),
                            ),
                          ),
                        ],
                      ),
                    );
                  }).toList(),
                ),
              ),
              const SizedBox(height: 12),
            ],

            // Execution Error Banner
            if (_executionError != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Text(
                  _executionError!,
                  style: const TextStyle(color: Colors.red, fontWeight: FontWeight.bold, fontSize: 13),
                ),
              ),

            // Security Disclaimer
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Colors.blue.shade50,
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Icon(Icons.shield_outlined, size: 18, color: Colors.blue.shade700),
                  const SizedBox(width: 8),
                  const Expanded(
                    child: Text(
                      'Seguridad: Toda acción requiere tu confirmación explícita. No se ejecuta código arbitrario ni comandos SQL.',
                      style: TextStyle(fontSize: 11, color: Color(0xFF1E3A8A)),
                    ),
                  ),
                ],
              ),
            ),

            const SizedBox(height: 20),

            // Action Buttons
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: _applying ? null : () => Navigator.of(context).pop(false),
                    style: OutlinedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    child: const Text('Cancelar'),
                  ),
                ),
                const SizedBox(width: 8),
                if (proposal.action != AiCrudAction.delete && proposal.action != AiCrudAction.search) ...[
                  Expanded(
                    child: OutlinedButton(
                      onPressed: _applying ? null : _openInForm,
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                      ),
                      child: const Text('Editar form'),
                    ),
                  ),
                  const SizedBox(width: 8),
                ],
                Expanded(
                  flex: 2,
                  child: ElevatedButton(
                    onPressed: (_applying || !proposal.isValid) ? null : _applyProposal,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: color,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    child: _applying
                        ? const SizedBox(
                            height: 18,
                            width: 18,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                          )
                        : Text('Confirmar y ${proposal.action.label}'),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
