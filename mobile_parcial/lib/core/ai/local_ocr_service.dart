import 'package:flutter/foundation.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:image_picker/image_picker.dart';
import 'package:ventas/core/ai/ai_entity_registry.dart';
import 'package:ventas/core/ai/ai_models.dart';

class ImageValidationResult {
  final bool isValid;
  final String? error;
  final String? mimeType;
  final int fileSizeBytes;

  const ImageValidationResult({
    required this.isValid,
    this.error,
    this.mimeType,
    this.fileSizeBytes = 0,
  });
}

class LocalOcrService extends ChangeNotifier {
  static final LocalOcrService instance = LocalOcrService._();
  final ImagePicker _picker = ImagePicker();

  static const int maxFileSizeBytes = 10 * 1024 * 1024; // 10 MB

  LocalOcrService._();

  /// Captures an image from camera or gallery and validates it
  Future<XFile?> pickImage(ImageSource source) async {
    try {
      final file = await _picker.pickImage(
        source: source,
        maxWidth: 1920,
        maxHeight: 1080,
        imageQuality: 85,
      );
      return file;
    } catch (e) {
      debugPrint('Error al capturar imagen'); // Sanitized log, no image data
      return null;
    }
  }

  /// Validates that the file exists, conforms to <=10MB and has a valid image format
  Future<ImageValidationResult> validateImageFile(XFile file) async {
    try {
      final bytes = await file.readAsBytes();
      return validateImageBytes(bytes, file.name);
    } catch (e) {
      return const ImageValidationResult(
        isValid: false,
        error: 'No se pudo leer el archivo de imagen',
      );
    }
  }

  ImageValidationResult validateImageBytes(Uint8List bytes, String filename) {
    if (bytes.isEmpty) {
      return const ImageValidationResult(
        isValid: false,
        error: 'El archivo de imagen está vacío',
      );
    }

    if (bytes.length > maxFileSizeBytes) {
      return ImageValidationResult(
        isValid: false,
        error: 'La imagen excede el límite máximo de 10 MB (${(bytes.length / (1024 * 1024)).toStringAsFixed(1)} MB)',
        fileSizeBytes: bytes.length,
      );
    }

    // Inspect magic bytes
    final mime = _detectMimeType(bytes);
    if (mime == null) {
      return ImageValidationResult(
        isValid: false,
        error: 'Formato no soportado. Debe ser PNG, JPEG o WebP',
        fileSizeBytes: bytes.length,
      );
    }

    return ImageValidationResult(
      isValid: true,
      mimeType: mime,
      fileSizeBytes: bytes.length,
    );
  }

  String? _detectMimeType(Uint8List bytes) {
    if (bytes.length >= 8 &&
        bytes[0] == 0x89 &&
        bytes[1] == 0x50 &&
        bytes[2] == 0x4E &&
        bytes[3] == 0x47) {
      return 'image/png';
    }
    if (bytes.length >= 3 && bytes[0] == 0xFF && bytes[1] == 0xD8 && bytes[2] == 0xFF) {
      return 'image/jpeg';
    }
    if (bytes.length >= 12 &&
        bytes[0] == 0x52 &&
        bytes[1] == 0x49 &&
        bytes[2] == 0x46 &&
        bytes[3] == 0x46 &&
        bytes[8] == 0x57 &&
        bytes[9] == 0x45 &&
        bytes[10] == 0x42 &&
        bytes[11] == 0x50) {
      return 'image/webp';
    }

    return null;
  }

  /// Extracts structured fields from an image (using offline text & pattern recognition)
  Future<AiCrudProposal> extractFromImage(XFile imageFile, {String? manualOcrText}) async {
    final validation = await validateImageFile(imageFile);
    if (!validation.isValid) {
      return AiCrudProposal(
        action: AiCrudAction.create,
        entityType: '',
        validationErrors: [validation.error ?? 'Imagen inválida'],
        source: AiProposalSource.ocrLocal,
        rawCommandSummary: 'Error en validación de imagen',
      );
    }

    // Extract text from image or use provided OCR text
    try {
      final text = manualOcrText ?? await _extractRawTextFromImage(imageFile);
      if (text.trim().isEmpty) {
        return AiCrudProposal(
          action: AiCrudAction.search,
          entityType: 'Producto',
          validationErrors: const ['No se detectó texto legible en la imagen'],
          source: AiProposalSource.ocrLocal,
          rawCommandSummary: 'OCR local sin texto legible',
        );
      }
      return parseOcrText(text);
    } catch (_) {
      return AiCrudProposal(
        action: AiCrudAction.search,
        entityType: 'Producto',
        validationErrors: const ['No se pudo procesar la imagen con el OCR local'],
        source: AiProposalSource.ocrLocal,
        rawCommandSummary: 'Error de OCR local',
      );
    }
  }

  /// Parses text extracted from OCR into structured entity fields
  AiCrudProposal parseOcrText(String text) {
    final clean = text.replaceAll('\r', '\n');
    final lines = clean.split('\n').map((l) => l.trim()).where((l) => l.isNotEmpty).toList();

    // Heuristic detection: what entity is represented?
    // 1. Check Factura
    if (_matchesFactura(clean)) {
      final payload = <String, dynamic>{};
      final numMatch = RegExp(r'(?:factura|nro|numero|folio|invoice)[\s:#*-]*([A-Z0-9\-_]{2,15})', caseSensitive: false).firstMatch(clean);
      if (numMatch != null) {
        payload['numeroFactura'] = numMatch.group(1);
      } else {
        // Look for standalone invoice pattern like F001-1234
        final standMatch = RegExp(r'\b([A-Z]{1,3}-\d{3,10})\b').firstMatch(clean);
        if (standMatch != null) payload['numeroFactura'] = standMatch.group(1);
      }

      final amountMatch = RegExp(r'(?:total|monto|importe|subtotal|a pagar|bs|usd|\$)[\s:#*-]*(\d+(?:[.,]\d{1,2})?)', caseSensitive: false).firstMatch(clean);
      if (amountMatch != null) {
        final rawNum = amountMatch.group(1)!.replaceAll(',', '.');
        payload['monto'] = double.tryParse(rawNum) ?? 0.0;
      }

      final entityMeta = AiEntityRegistry.instance.findEntity('factura');
      final errors = entityMeta?.validatePayload(payload, isCreate: true) ?? [];

      return AiCrudProposal(
        action: AiCrudAction.create,
        entityType: 'Factura',
        payload: payload,
        validationErrors: errors,
        confidence: 0.90,
        source: AiProposalSource.ocrLocal,
        rawCommandSummary: 'OCR Factura extraída (${payload.length} campos)',
      );
    }

    // 2. Check Producto
    if (_matchesProducto(clean)) {
      final payload = <String, dynamic>{};
      final codeMatch = RegExp(r'(?:codigo|sku|ref|articulo|code)[\s:#*-]*([A-Z0-9\-_]{2,15})', caseSensitive: false).firstMatch(clean);
      if (codeMatch != null) {
        payload['codigo'] = codeMatch.group(1);
      } else {
        final standCode = RegExp(r'\b(PROD-[A-Z0-9]+|[A-Z]{2,4}-\d{2,6})\b').firstMatch(clean);
        if (standCode != null) payload['codigo'] = standCode.group(1);
      }

      final priceMatch = RegExp(r'(?:precio|costo|p\.v\.p|\$)[\s:#*-]*(\d+(?:[.,]\d{1,2})?)', caseSensitive: false).firstMatch(clean);
      if (priceMatch != null) {
        final rawNum = priceMatch.group(1)!.replaceAll(',', '.');
        payload['precio'] = double.tryParse(rawNum) ?? 0.0;
      }

      final stockMatch = RegExp(r'(?:stock|cantidad|cant|existencia)[\s:#*-]*(\d+)', caseSensitive: false).firstMatch(clean);
      if (stockMatch != null) {
        payload['stock'] = int.tryParse(stockMatch.group(1)!) ?? 0;
      }

      final entityMeta = AiEntityRegistry.instance.findEntity('producto');
      final errors = entityMeta?.validatePayload(payload, isCreate: true) ?? [];

      return AiCrudProposal(
        action: AiCrudAction.create,
        entityType: 'Producto',
        payload: payload,
        validationErrors: errors,
        confidence: 0.88,
        source: AiProposalSource.ocrLocal,
        rawCommandSummary: 'OCR Producto extraído (${payload.length} campos)',
      );
    }

    // 3. Check Cliente
    final emailMatch = RegExp(r'([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})').firstMatch(clean);
    if (emailMatch != null || _matchesCliente(clean)) {
      final payload = <String, dynamic>{};
      if (emailMatch != null) {
        payload['email'] = emailMatch.group(1);
      }

      final nameMatch = RegExp(r'(?:cliente|nombre|senor|sr|nombre completo)[\s:#*-]*([A-Za-zÁÉÍÓÚáéíóúñÑ ]{3,30})', caseSensitive: false).firstMatch(clean);
      if (nameMatch != null) {
        payload['nombre'] = nameMatch.group(1)!.trim();
      } else if (lines.isNotEmpty && !lines.first.contains('@') && !lines.first.contains(':')) {
        payload['nombre'] = lines.first;
      }

      final entityMeta = AiEntityRegistry.instance.findEntity('cliente');
      final errors = entityMeta?.validatePayload(payload, isCreate: true) ?? [];

      return AiCrudProposal(
        action: AiCrudAction.create,
        entityType: 'Cliente',
        payload: payload,
        validationErrors: errors,
        confidence: 0.85,
        source: AiProposalSource.ocrLocal,
        rawCommandSummary: 'OCR Cliente extraído (${payload.length} campos)',
      );
    }

    // 4. Default Search proposal if a single code / barcode / number was detected
    final singleCode = RegExp(r'\b([A-Za-z0-9\-_]{3,20})\b').firstMatch(clean)?.group(1);
    return AiCrudProposal(
      action: AiCrudAction.search,
      entityType: 'Producto',
      recordId: singleCode ?? clean.trim(),
      payload: {'query': singleCode ?? clean.trim()},
      confidence: 0.70,
      source: AiProposalSource.ocrLocal,
      rawCommandSummary: 'OCR Búsqueda de código detectado',
    );
  }

  bool _matchesFactura(String text) {
    final lower = text.toLowerCase();
    return lower.contains('factura') ||
        lower.contains('recibo') ||
        lower.contains('monto') ||
        lower.contains('invoice') ||
        lower.contains('a pagar') ||
        lower.contains('total:');
  }

  bool _matchesProducto(String text) {
    final lower = text.toLowerCase();
    return lower.contains('producto') ||
        lower.contains('precio') ||
        lower.contains('stock') ||
        lower.contains('sku') ||
        lower.contains('articulo');
  }

  bool _matchesCliente(String text) {
    final lower = text.toLowerCase();
    return lower.contains('cliente') || lower.contains('email') || lower.contains('correo');
  }

  Future<String> _extractRawTextFromImage(XFile file) async {
    final recognizer = TextRecognizer(script: TextRecognitionScript.latin);
    try {
      final input = InputImage.fromFilePath(file.path);
      final recognized = await recognizer.processImage(input);
      return recognized.text;
    } finally {
      await recognizer.close();
    }
  }
}
