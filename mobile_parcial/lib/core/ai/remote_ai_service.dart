import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';
import 'package:ventas/core/api/api_client.dart';
import 'package:ventas/core/config/app_config.dart';
import 'package:ventas/core/sync/sync_service.dart';
import 'package:ventas/core/ai/ai_models.dart';
import 'package:ventas/core/ai/local_ocr_service.dart';

class RemoteAiService {
  static final RemoteAiService instance = RemoteAiService._();
  final ApiClient _client = ApiClient.instance;
  final SyncService _sync = SyncService.instance;

  RemoteAiService._();

  bool get canUseRemoteAi => _sync.isOnline;

  Future<AiCrudProposal> analyzeVisualComplex({
    required XFile imageFile,
    String? prompt,
  }) async {
    // Check connectivity first - never block offline flow!
    if (!_sync.isOnline) {
      // Fallback immediately to local OCR offline
      return await LocalOcrService.instance.extractFromImage(imageFile);
    }

    try {
      final validation = await LocalOcrService.instance.validateImageFile(imageFile);
      if (!validation.isValid) {
        return AiCrudProposal(
          action: AiCrudAction.create,
          entityType: '',
          validationErrors: [validation.error ?? 'Imagen no válida'],
          source: AiProposalSource.remoteAi,
          rawCommandSummary: 'Error en validación de imagen',
        );
      }

      final baseUrl = await AppConfig.getBaseUrl();
      final uri = Uri.parse('$baseUrl/api/v1/ai/mobile-analyze');
      final request = http.MultipartRequest('POST', uri);

      final token = _client.token;
      if (token != null) {
        request.headers['Authorization'] = 'Bearer $token';
      }

      final bytes = await imageFile.readAsBytes();
      request.files.add(http.MultipartFile.fromBytes(
        'file',
        bytes,
        filename: imageFile.name,
      ));

      if (prompt != null && prompt.trim().isNotEmpty) {
        request.fields['prompt'] = prompt.trim();
      }

      final streamedResponse = await request.send().timeout(const Duration(seconds: 15));
      final responseBody = await streamedResponse.stream.bytesToString();

      if (streamedResponse.statusCode >= 200 && streamedResponse.statusCode < 300) {
        final data = jsonDecode(responseBody) as Map<String, dynamic>;
        return AiCrudProposal(
          action: _parseAction(data['action']?.toString()),
          entityType: data['entity']?.toString() ?? data['entityType']?.toString() ?? 'Factura',
          recordId: data['id']?.toString() ?? data['recordId']?.toString(),
          payload: data['data'] is Map ? Map<String, dynamic>.from(data['data']) : {},
          confidence: (data['confidence'] as num?)?.toDouble() ?? 0.95,
          source: AiProposalSource.remoteAi,
          rawCommandSummary: 'Análisis visual remoto completado',
        );
      } else {
        // Remote returned error or 503; gracefully fallback to local OCR
        debugPrint('Servidor de IA remota no disponible (status: ${streamedResponse.statusCode}). Usando OCR local.');
        return await LocalOcrService.instance.extractFromImage(imageFile);
      }
    } catch (e) {
      // Network timeout or exception; fallback gracefully without blocking offline usage
      debugPrint('Fallo al conectar con IA remota. Usando procesamiento local offline.');
      return await LocalOcrService.instance.extractFromImage(imageFile);
    }
  }

  AiCrudAction _parseAction(String? actionStr) {
    if (actionStr == null) return AiCrudAction.create;
    switch (actionStr.toLowerCase()) {
      case 'create':
      case 'crear':
        return AiCrudAction.create;
      case 'update':
      case 'editar':
      case 'actualizar':
        return AiCrudAction.update;
      case 'delete':
      case 'eliminar':
        return AiCrudAction.delete;
      case 'search':
      case 'buscar':
        return AiCrudAction.search;
      default:
        return AiCrudAction.create;
    }
  }
}
