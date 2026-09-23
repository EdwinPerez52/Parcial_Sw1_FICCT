// ignore_for_file: prefer_const_constructors

import 'package:flutter/material.dart';
import 'package:ventas/core/i18n/app_strings.dart';
import 'package:ventas/presentation/state/auth_provider.dart';
import 'package:ventas/presentation/screens/auth/login_screen.dart';
import 'package:ventas/presentation/screens/settings/server_settings_screen.dart';
import 'package:ventas/core/sync/sync_service.dart';
import 'package:ventas/core/widgets/sync_status_badge.dart';
import 'package:ventas/presentation/screens/conflicts/conflict_resolution_screen.dart';
import 'package:ventas/presentation/screens/cliente/cliente_list_screen.dart';
import 'package:ventas/presentation/screens/pedido/pedido_list_screen.dart';
import 'package:ventas/presentation/screens/factura/factura_list_screen.dart';
import 'package:ventas/presentation/screens/producto/producto_list_screen.dart';
import 'package:ventas/presentation/screens/ai/ai_assistant_screen.dart';


class HomeScreen extends StatelessWidget {
  final AuthProvider authProvider;
  const HomeScreen({super.key, required this.authProvider});

  @override
  Widget build(BuildContext context) {
    final user = authProvider.user;
    return Scaffold(
      appBar: AppBar(
        title: const Text(AppStrings.appName),
        actions: [
          const SyncStatusBadge(),
          IconButton(
            icon: const Icon(Icons.auto_awesome),
            tooltip: 'Asistente IA (Texto, Voz, Foto)',
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const AiAssistantScreen()),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.settings),
            tooltip: AppStrings.serverSettings,
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const ServerSettingsScreen()),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.logout),
            tooltip: AppStrings.logout,
            onPressed: () async {
              await authProvider.logout();
              if (context.mounted) {
                Navigator.of(context).pushReplacement(
                  MaterialPageRoute(builder: (_) => LoginScreen(authProvider: authProvider)),
                );
              }
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 16),
        children: [
          AnimatedBuilder(
            animation: SyncService.instance,
            builder: (context, _) {
              final sync = SyncService.instance;
              if (sync.conflictCount == 0) return const SizedBox.shrink();
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
                child: Material(
                  color: Colors.red.shade50,
                  borderRadius: BorderRadius.circular(12),
                  child: InkWell(
                    onTap: () {
                      Navigator.push(
                        context,
                        MaterialPageRoute(builder: (_) => const ConflictResolutionScreen()),
                      );
                    },
                    borderRadius: BorderRadius.circular(12),
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Row(
                        children: [
                          Icon(Icons.warning_amber_rounded, color: Colors.red.shade700, size: 28),
                          const SizedBox(width: 12),
                          Expanded(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text(
                                  '¡${sync.conflictCount} conflicto(s) de sincronización!',
                                  style: TextStyle(
                                    color: Colors.red.shade900,
                                    fontWeight: FontWeight.bold,
                                    fontSize: 14,
                                  ),
                                ),
                                Text(
                                  'Toca para comparar y resolver.',
                                  style: TextStyle(
                                    color: Colors.red.shade800,
                                    fontSize: 12,
                                  ),
                                ),
                              ],
                            ),
                          ),
                          Icon(Icons.chevron_right, color: Colors.red.shade700),
                        ],
                      ),
                    ),
                  ),
                ),
              );
            },
          ),
          if (user != null)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFF1E3A8A), Color(0xFF2563EB)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Row(
                  children: [
                    CircleAvatar(
                      radius: 24,
                      backgroundColor: Colors.white24,
                      child: Text(
                        user.fullName.isNotEmpty ? user.fullName[0].toUpperCase() : 'U',
                        style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold),
                      ),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            user.fullName,
                            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
                          ),
                          Text(
                            '${user.email} (${user.role})',
                            style: const TextStyle(color: Colors.white70, fontSize: 12),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 4.0),
            child: Material(
              color: Colors.blue.shade50,
              borderRadius: BorderRadius.circular(12),
              child: InkWell(
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const AiAssistantScreen()),
                ),
                borderRadius: BorderRadius.circular(12),
                child: Padding(
                  padding: const EdgeInsets.all(14),
                  child: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: const Color(0xFF1E3A8A),
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: const Icon(Icons.auto_awesome, color: Colors.white, size: 22),
                      ),
                      const SizedBox(width: 14),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Asistente IA Móvil',
                              style: TextStyle(
                                color: Color(0xFF1E3A8A),
                                fontWeight: FontWeight.bold,
                                fontSize: 15,
                              ),
                            ),
                            Text(
                              'Opera datos por Texto, Voz y Foto sin red',
                              style: TextStyle(
                                color: Colors.blue.shade900,
                                fontSize: 12,
                              ),
                            ),
                          ],
                        ),
                      ),
                      const Icon(Icons.chevron_right, color: Color(0xFF1E3A8A)),
                    ],
                  ),
                ),
              ),
            ),
          ),
          const Padding(
            padding: EdgeInsets.fromLTRB(18, 16, 18, 8),
            child: Text(
              'Entidades del Modelo',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold, color: Color(0xFF1E3A8A)),
            ),
          ),
      Card(
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
          leading: const CircleAvatar(
            backgroundColor: Color(0x1F1E3A8A),
            child: Icon(Icons.table_chart_outlined, color: Color(0xFF1E3A8A)),
          ),
          title: Text(
            'Cliente',
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
          ),
          subtitle: Text(
            'Tabla: cliente • Atributos: 3',
            style: const TextStyle(fontSize: 13, color: Colors.grey),
          ),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const ClienteListScreen()),
          ),
        ),
      ),
      Card(
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
          leading: const CircleAvatar(
            backgroundColor: Color(0x1F1E3A8A),
            child: Icon(Icons.table_chart_outlined, color: Color(0xFF1E3A8A)),
          ),
          title: Text(
            'Pedido',
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
          ),
          subtitle: Text(
            'Tabla: pedido • Atributos: 4',
            style: const TextStyle(fontSize: 13, color: Colors.grey),
          ),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const PedidoListScreen()),
          ),
        ),
      ),
      Card(
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
          leading: const CircleAvatar(
            backgroundColor: Color(0x1F1E3A8A),
            child: Icon(Icons.table_chart_outlined, color: Color(0xFF1E3A8A)),
          ),
          title: Text(
            'Factura',
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
          ),
          subtitle: Text(
            'Tabla: factura • Atributos: 3',
            style: const TextStyle(fontSize: 13, color: Colors.grey),
          ),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const FacturaListScreen()),
          ),
        ),
      ),
      Card(
        child: ListTile(
          contentPadding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10),
          leading: const CircleAvatar(
            backgroundColor: Color(0x1F1E3A8A),
            child: Icon(Icons.table_chart_outlined, color: Color(0xFF1E3A8A)),
          ),
          title: Text(
            'Producto',
            style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
          ),
          subtitle: Text(
            'Tabla: producto • Atributos: 4',
            style: const TextStyle(fontSize: 13, color: Colors.grey),
          ),
          trailing: const Icon(Icons.chevron_right),
          onTap: () => Navigator.of(context).push(
            MaterialPageRoute(builder: (_) => const ProductoListScreen()),
          ),
        ),
      ),

        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.of(context).push(
          MaterialPageRoute(builder: (_) => const AiAssistantScreen()),
        ),
        icon: const Icon(Icons.auto_awesome),
        label: const Text('Asistente IA'),
        backgroundColor: const Color(0xFF1E3A8A),
        foregroundColor: Colors.white,
      ),
    );
  }
}
