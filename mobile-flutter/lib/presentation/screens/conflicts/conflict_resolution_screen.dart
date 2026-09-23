import 'package:flutter/material.dart';
import 'package:ventas/core/sync/sync_service.dart';

class ConflictResolutionScreen extends StatefulWidget {
  const ConflictResolutionScreen({super.key});

  @override
  State<ConflictResolutionScreen> createState() => _ConflictResolutionScreenState();
}

class _ConflictResolutionScreenState extends State<ConflictResolutionScreen> {
  final SyncService _syncService = SyncService.instance;
  List<ConflictRecord> _conflicts = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    _loadConflicts();
  }

  Future<void> _loadConflicts() async {
    setState(() => _isLoading = true);
    final list = await _syncService.getOpenConflicts();
    setState(() {
      _conflicts = list;
      _isLoading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Resolución de Conflictos'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _loadConflicts,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _conflicts.isEmpty
              ? _buildEmptyState()
              : ListView.builder(
                  padding: const EdgeInsets.all(16),
                  itemCount: _conflicts.length,
                  itemBuilder: (context, index) {
                    final conflict = _conflicts[index];
                    return _buildConflictCard(conflict);
                  },
                ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.check_circle_outline, size: 64, color: Colors.green.shade600),
          const SizedBox(height: 16),
          const Text(
            '¡No hay conflictos pendientes!',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          const Text(
            'Todos los cambios locales y remotos están sincronizados.',
            style: TextStyle(color: Colors.grey),
          ),
        ],
      ),
    );
  }

  Widget _buildConflictCard(ConflictRecord conflict) {
    final allKeys = <String>{
      ...conflict.localPayload.keys,
      ...conflict.serverPayload.keys,
    }..removeWhere((k) => k.startsWith('_'));

    return Card(
      margin: const EdgeInsets.only(bottom: 16),
      elevation: 3,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: Colors.red.shade300, width: 1.5),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.red.shade100,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    conflict.entity.toUpperCase(),
                    style: TextStyle(
                      color: Colors.red.shade900,
                      fontWeight: FontWeight.bold,
                      fontSize: 12,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Registro: ${conflict.recordId}',
                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Text(
              'Campos en conflicto: ${conflict.conflictingFields.join(', ')}',
              style: TextStyle(color: Colors.red.shade700, fontWeight: FontWeight.w600, fontSize: 13),
            ),
            const Divider(height: 24),
            Table(
              border: TableBorder.all(color: Colors.grey.shade300, borderRadius: BorderRadius.circular(4)),
              columnWidths: const {
                0: FlexColumnWidth(1.2),
                1: FlexColumnWidth(2),
                2: FlexColumnWidth(2),
              },
              children: [
                TableRow(
                  decoration: BoxDecoration(color: Colors.grey.shade100),
                  children: const [
                    Padding(
                      padding: EdgeInsets.all(8),
                      child: Text('Campo', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                    ),
                    Padding(
                      padding: EdgeInsets.all(8),
                      child: Text('Valor Local (Móvil)', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.blue, fontSize: 12)),
                    ),
                    Padding(
                      padding: EdgeInsets.all(8),
                      child: Text('Valor Servidor (Remoto)', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.purple, fontSize: 12)),
                    ),
                  ],
                ),
                for (final key in allKeys)
                  _buildComparisonRow(
                    field: key,
                    localVal: conflict.localPayload[key]?.toString() ?? '—',
                    serverVal: conflict.serverPayload[key]?.toString() ?? '—',
                    isConflict: conflict.conflictingFields.contains(key),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    icon: const Icon(Icons.phone_android, size: 16),
                    label: const Text('Conservar local'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.blue.shade700,
                      foregroundColor: Colors.white,
                    ),
                    onPressed: () async {
                      await _syncService.resolveConflictKeepLocal(conflict.id);
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Se conservó el valor local en el servidor.')),
                      );
                      await _loadConflicts();
                    },
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton.icon(
                    icon: const Icon(Icons.cloud_download, size: 16),
                    label: const Text('Descartar local'),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.purple.shade700,
                    ),
                    onPressed: () async {
                      await _syncService.resolveConflictDiscardLocal(conflict.id);
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('Se descartó el cambio local y se adoptó el servidor.')),
                      );
                      await _loadConflicts();
                    },
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: TextButton.icon(
                icon: const Icon(Icons.edit, size: 16),
                label: const Text('Editar de nuevo y resolver manualmente'),
                onPressed: () => _showManualEditDialog(conflict),
              ),
            ),
          ],
        ),
      ),
    );
  }

  TableRow _buildComparisonRow({
    required String field,
    required String localVal,
    required String serverVal,
    required bool isConflict,
  }) {
    return TableRow(
      decoration: BoxDecoration(
        color: isConflict ? Colors.red.shade50 : null,
      ),
      children: [
        Padding(
          padding: const EdgeInsets.all(8),
          child: Text(
            field,
            style: TextStyle(
              fontWeight: isConflict ? FontWeight.bold : FontWeight.normal,
              color: isConflict ? Colors.red.shade900 : Colors.black87,
              fontSize: 12,
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.all(8),
          child: Text(
            localVal,
            style: TextStyle(
              fontWeight: isConflict ? FontWeight.bold : FontWeight.normal,
              color: isConflict ? Colors.blue.shade900 : Colors.black87,
              fontSize: 12,
            ),
          ),
        ),
        Padding(
          padding: const EdgeInsets.all(8),
          child: Text(
            serverVal,
            style: TextStyle(
              fontWeight: isConflict ? FontWeight.bold : FontWeight.normal,
              color: isConflict ? Colors.purple.shade900 : Colors.black87,
              fontSize: 12,
            ),
          ),
        ),
      ],
    );
  }

  void _showManualEditDialog(ConflictRecord conflict) {
    final Map<String, TextEditingController> controllers = {};
    final allKeys = <String>{
      ...conflict.localPayload.keys,
      ...conflict.serverPayload.keys,
    }..removeWhere((k) => k.startsWith('_') || k == 'id');

    for (final key in allKeys) {
      controllers[key] = TextEditingController(
        text: conflict.localPayload[key]?.toString() ??
            conflict.serverPayload[key]?.toString() ??
            '',
      );
    }

    showDialog(
      context: context,
      builder: (dialogCtx) {
        return AlertDialog(
          title: Text('Editar y fusionar ${conflict.entity}'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                for (final key in allKeys)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: TextField(
                      controller: controllers[key],
                      decoration: InputDecoration(
                        labelText: key,
                        helperText: 'Servidor: ${conflict.serverPayload[key]} | Local: ${conflict.localPayload[key]}',
                        border: const OutlineInputBorder(),
                      ),
                    ),
                  ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(dialogCtx),
              child: const Text('Cancelar'),
            ),
            ElevatedButton(
              onPressed: () async {
                final merged = <String, dynamic>{};
                for (final entry in controllers.entries) {
                  merged[entry.key] = entry.value.text;
                }
                Navigator.pop(dialogCtx);
                await _syncService.resolveConflictManual(conflict.id, merged);
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('Conflicto resuelto con los valores editados.')),
                );
                await _loadConflicts();
              },
              child: const Text('Guardar y sincronizar'),
            ),
          ],
        );
      },
    );
  }
}
