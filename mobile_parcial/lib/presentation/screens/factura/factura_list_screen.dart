import 'package:flutter/material.dart';
import 'package:ventas/core/widgets/async_state_view.dart';
import 'package:ventas/core/widgets/confirm_dialog.dart';
import 'package:ventas/core/i18n/app_strings.dart';
import 'package:ventas/data/models/factura.dart';
import 'package:ventas/presentation/state/factura_provider.dart';
import 'package:ventas/presentation/screens/factura/factura_form_screen.dart';
import 'package:ventas/presentation/screens/factura/factura_detail_screen.dart';

class FacturaListScreen extends StatefulWidget {
  const FacturaListScreen({super.key});

  @override
  State<FacturaListScreen> createState() => _FacturaListScreenState();
}

class _FacturaListScreenState extends State<FacturaListScreen> {
  final FacturaProvider _provider = FacturaProvider();
  final TextEditingController _searchController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _provider.loadItems();
  }

  @override
  void dispose() {
    _searchController.dispose();
    _provider.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Factura'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: AppStrings.retry,
            onPressed: () => _provider.loadItems(),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final created = await Navigator.of(context).push<Factura>(
            MaterialPageRoute(builder: (_) => const FacturaFormScreen()),
          );
          if (created != null) {
            _provider.loadItems();
          }
        },
        icon: const Icon(Icons.add),
        label: const Text('Nuevo Factura'),
      ),
      body: Column(
        children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            color: Colors.white,
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: 'Buscar en Factura...',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: () {
                          _searchController.clear();
                          _provider.setSearchQuery('');
                        },
                      )
                    : null,
              ),
              onChanged: (val) => _provider.setSearchQuery(val),
            ),
          ),
          Expanded(
            child: ListenableBuilder(
              listenable: _provider,
              builder: (ctx, _) {
                return AsyncStateView(
                  isLoading: _provider.isLoading,
                  errorMessage: _provider.errorMessage,
                  isEmpty: _provider.items.isEmpty,
                  onRetry: () => _provider.loadItems(),
                  child: RefreshIndicator(
                    onRefresh: () => _provider.loadItems(),
                    child: ListView.builder(
                      itemCount: _provider.items.length,
                      padding: const EdgeInsets.only(bottom: 80, top: 8),
                      itemBuilder: (context, index) {
                        final item = _provider.items[index];
                        return Card(
                          child: ListTile(
                            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                            leading: const CircleAvatar(
                              backgroundColor: Color(0x1F1E3A8A),
                              child: Icon(Icons.folder_outlined, color: Color(0xFF1E3A8A)),
                            ),
                            title: Text(
                              item.displayLabel,
                              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                            ),
                            subtitle: Text(
                              'ID: ${item.id}',
                              style: const TextStyle(fontSize: 12, color: Colors.grey),
                            ),
                            trailing: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                IconButton(
                                  icon: const Icon(Icons.edit, color: Colors.blue),
                                  onPressed: () async {
                                    final updated = await Navigator.of(context).push<Factura>(
                                      MaterialPageRoute(builder: (_) => FacturaFormScreen(item: item)),
                                    );
                                    if (updated != null) {
                                      _provider.loadItems();
                                    }
                                  },
                                ),
                                IconButton(
                                  icon: const Icon(Icons.delete_outline, color: Colors.red),
                                  onPressed: () async {
                                    final confirm = await ConfirmDialog.show(context);
                                    if (confirm) {
                                      await _provider.deleteItem(item.id);
                                    }
                                  },
                                ),
                              ],
                            ),
                            onTap: () async {
                              await Navigator.of(context).push(
                                MaterialPageRoute(builder: (_) => FacturaDetailScreen(item: item)),
                              );
                              _provider.loadItems();
                            },
                          ),
                        );
                      },
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
