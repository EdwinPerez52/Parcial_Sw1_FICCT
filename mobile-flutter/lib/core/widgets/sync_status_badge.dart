import 'package:flutter/material.dart';
import 'package:ventas/core/sync/sync_service.dart';
import 'package:ventas/presentation/screens/conflicts/conflict_resolution_screen.dart';

class SyncStatusBadge extends StatelessWidget {
  const SyncStatusBadge({super.key});

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: SyncService.instance,
      builder: (context, _) {
        final sync = SyncService.instance;
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Tooltip(
              message: sync.isOnline ? 'Conectado al servidor' : 'Modo sin conexión (Offline)',
              child: InkWell(
                onTap: () => sync.syncAll(),
                borderRadius: BorderRadius.circular(12),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(
                        width: 9,
                        height: 9,
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          color: sync.isOnline ? Colors.green : Colors.grey,
                        ),
                      ),
                      const SizedBox(width: 4),
                      Text(
                        sync.isOnline ? 'Online' : 'Offline',
                        style: TextStyle(
                          fontSize: 12,
                          color: sync.isOnline ? Colors.white : Colors.white70,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
            if (sync.pendingCount > 0) ...[
              const SizedBox(width: 4),
              Tooltip(
                message: '${sync.pendingCount} operaciones pendientes en outbox',
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: Colors.orange.shade800,
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.cloud_upload, size: 12, color: Colors.white),
                      const SizedBox(width: 3),
                      Text(
                        '${sync.pendingCount}',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ],
            if (sync.conflictCount > 0) ...[
              const SizedBox(width: 4),
              Tooltip(
                message: '${sync.conflictCount} conflicto(s) por resolver',
                child: InkWell(
                  onTap: () {
                    Navigator.push(
                      context,
                      MaterialPageRoute(
                        builder: (_) => const ConflictResolutionScreen(),
                      ),
                    );
                  },
                  borderRadius: BorderRadius.circular(10),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: Colors.red.shade700,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.warning_amber_rounded, size: 13, color: Colors.white),
                        const SizedBox(width: 2),
                        Text(
                          '${sync.conflictCount}',
                          style: const TextStyle(
                            color: Colors.white,
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ],
            IconButton(
              icon: sync.isSyncing
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                      ),
                    )
                  : const Icon(Icons.sync, size: 20),
              tooltip: 'Sincronizar ahora',
              onPressed: sync.isSyncing ? null : () => sync.syncAll(),
            ),
          ],
        );
      },
    );
  }
}
