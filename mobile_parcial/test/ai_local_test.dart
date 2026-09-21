import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:ventas/core/ai/ai_command_interpreter.dart';
import 'package:ventas/core/ai/ai_entity_registry.dart';
import 'package:ventas/core/ai/ai_models.dart';
import 'package:ventas/core/ai/local_ocr_service.dart';
import 'package:ventas/core/ai/speech_recognition_service.dart';
import 'package:ventas/core/database/app_database.dart';
import 'package:ventas/core/sync/outbox_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUpAll(() {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    AppDatabase.databaseName = 'ai_local_test.db';
  });

  setUp(() async {
    await AppDatabase.instance.clearAll();
  });

  group('1. Local Text Command Interpreter (100% Offline)', () {
    final interpreter = AiCommandInterpreter.instance;

    test('Parses valid CREATE Cliente command with natural language', () {
      final proposal = interpreter.interpret(
        'Crear cliente Carlos Gómez email carlos@empresa.com',
        source: AiProposalSource.textLocal,
      );

      expect(proposal.action, equals(AiCrudAction.create));
      expect(proposal.entityType, equals('Cliente'));
      expect(proposal.payload['nombre'], equals('Carlos Gomez'));
      expect(proposal.payload['email'], equals('carlos@empresa.com'));
      expect(proposal.validationErrors, isEmpty);
      expect(proposal.isValid, isTrue);
      expect(proposal.source, equals(AiProposalSource.textLocal));
    });

    test('Reuses form validation rules: flags missing required fields', () {
      final proposal = interpreter.interpret(
        'Nuevo cliente soloNombre',
        source: AiProposalSource.textLocal,
      );

      expect(proposal.action, equals(AiCrudAction.create));
      expect(proposal.entityType, equals('Cliente'));
      // email is required by Cliente form rules
      expect(proposal.validationErrors, isNotEmpty);
      expect(proposal.validationErrors.any((e) => e.contains('email')), isTrue);
      expect(proposal.isValid, isFalse);
    });

    test('Parses CREATE Producto with numeric types (decimal, integer)', () {
      final proposal = interpreter.interpret(
        'crear producto codigo PROD-01 precio 49.99 stock 150',
      );

      expect(proposal.action, equals(AiCrudAction.create));
      expect(proposal.entityType, equals('Producto'));
      expect(proposal.payload['codigo'], equals('PROD-01'));
      expect(proposal.payload['precio'], equals(49.99));
      expect(proposal.payload['stock'], equals(150));
      expect(proposal.validationErrors, isEmpty);
      expect(proposal.isValid, isTrue);
    });

    test('Rejects invalid numeric types according to schema', () {
      final proposal = interpreter.interpret(
        'crear producto codigo PROD-02 precio textoInvalido stock noNumero',
      );

      expect(proposal.validationErrors, isNotEmpty);
      expect(proposal.validationErrors.any((e) => e.contains('precio')), isTrue);
      expect(proposal.validationErrors.any((e) => e.contains('stock')), isTrue);
      expect(proposal.isValid, isFalse);
    });

    test('Parses CREATE Factura and CREATE Pedido with enum validation', () {
      final facturaProposal = interpreter.interpret(
        'nueva factura numeroFactura F-9021 monto 1500.50',
      );
      expect(facturaProposal.entityType, equals('Factura'));
      expect(facturaProposal.payload['numeroFactura'], equals('F-9021'));
      expect(facturaProposal.payload['monto'], equals(1500.50));
      expect(facturaProposal.isValid, isTrue);

      final pedidoProposal = interpreter.interpret(
        'crear pedido numero PED-99 total 350.00 estado NUEVO',
      );
      expect(pedidoProposal.entityType, equals('Pedido'));
      expect(pedidoProposal.payload['numero'], equals('PED-99'));
      expect(pedidoProposal.payload['total'], equals(350.00));
      expect(pedidoProposal.payload['estado'], equals('NUEVO'));
      expect(pedidoProposal.isValid, isTrue);
    });

    test('Rejects invalid enum values', () {
      final pedidoProposal = interpreter.interpret(
        'crear pedido numero PED-99 total 350.00 estado ESTADO_INEXISTENTE',
      );
      expect(pedidoProposal.validationErrors.any((e) => e.contains('estado')), isTrue);
      expect(pedidoProposal.isValid, isFalse);
    });

    test('Parses UPDATE command with recordId and partial payload', () {
      final proposal = interpreter.interpret(
        'actualizar producto id PROD-01 precio 59.99',
      );

      expect(proposal.action, equals(AiCrudAction.update));
      expect(proposal.entityType, equals('Producto'));
      expect(proposal.recordId, equals('PROD-01'));
      expect(proposal.payload['precio'], equals(59.99));
      expect(proposal.validationErrors, isEmpty);
      expect(proposal.isValid, isTrue);
    });

    test('Parses DELETE command and marks as destructive', () {
      final proposal = interpreter.interpret(
        'eliminar factura F-9021',
      );

      expect(proposal.action, equals(AiCrudAction.delete));
      expect(proposal.entityType, equals('Factura'));
      expect(proposal.recordId, equals('F-9021'));
      expect(proposal.isDestructive, isTrue);
      expect(proposal.validationErrors, isEmpty);
    });

    test('Parses SEARCH command', () {
      final proposal = interpreter.interpret(
        'buscar cliente Gomez',
      );

      expect(proposal.action, equals(AiCrudAction.search));
      expect(proposal.entityType, equals('Cliente'));
      expect(proposal.validationErrors, isEmpty);
    });
  });

  group('2. Speech Recognition Integration (Voz a Texto local)', () {
    final speechService = SpeechRecognitionService.instance;

    test('Converts voice transcription into structured CRUD proposal using local interpreter', () {
      const voiceText = 'crear cliente Maria Rodriguez email maria@test.com';
      final proposal = speechService.processVoiceTranscription(voiceText);

      expect(proposal.source, equals(AiProposalSource.voiceLocal));
      expect(proposal.action, equals(AiCrudAction.create));
      expect(proposal.entityType, equals('Cliente'));
      expect(proposal.payload['nombre'], equals('Maria Rodriguez'));
      expect(proposal.payload['email'], equals('maria@test.com'));
      expect(proposal.isValid, isTrue);
    });
  });

  group('3. Local OCR and Image Validation', () {
    final ocrService = LocalOcrService.instance;

    test('Image validation rejects files over 10MB', () {
      // 11 MB fake payload
      final largeBytes = Uint8List(11 * 1024 * 1024);
      final result = ocrService.validateImageBytes(largeBytes, 'large.jpg');

      expect(result.isValid, isFalse);
      expect(result.error, contains('excede el límite'));
    });

    test('Image validation accepts valid PNG magic bytes', () {
      final pngBytes = Uint8List.fromList([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]);
      final result = ocrService.validateImageBytes(pngBytes, 'receipt.png');

      expect(result.isValid, isTrue);
      expect(result.mimeType, equals('image/png'));
    });

    test('Image validation accepts valid JPEG magic bytes', () {
      final jpegBytes = Uint8List.fromList([0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10]);
      final result = ocrService.validateImageBytes(jpegBytes, 'photo.jpg');

      expect(result.isValid, isTrue);
      expect(result.mimeType, equals('image/jpeg'));
    });

    test('OCR extracts Factura fields from invoice receipt text', () {
      const receiptOcr = '''
        COMPROBANTE COMERCIAL
        FACTURA N°: F-8812
        FECHA: 2026-09-21
        TOTAL A PAGAR: 450.75
      ''';

      final proposal = ocrService.parseOcrText(receiptOcr);

      expect(proposal.source, equals(AiProposalSource.ocrLocal));
      expect(proposal.action, equals(AiCrudAction.create));
      expect(proposal.entityType, equals('Factura'));
      expect(proposal.payload['numeroFactura'], equals('F-8812'));
      expect(proposal.payload['monto'], equals(450.75));
      expect(proposal.isValid, isTrue);
    });

    test('OCR extracts Producto fields from product label text', () {
      const labelOcr = '''
        ETIQUETA DE PRODUCTO
        CODIGO: SKU-550
        PRECIO: 89.50
        STOCK: 35
      ''';

      final proposal = ocrService.parseOcrText(labelOcr);

      expect(proposal.source, equals(AiProposalSource.ocrLocal));
      expect(proposal.entityType, equals('Producto'));
      expect(proposal.payload['codigo'], equals('SKU-550'));
      expect(proposal.payload['precio'], equals(89.50));
      expect(proposal.payload['stock'], equals(35));
      expect(proposal.isValid, isTrue);
    });

    test('OCR proposes Search when single barcode/code is detected', () {
      const barcodeOcr = 'PROD-998877';
      final proposal = ocrService.parseOcrText(barcodeOcr);

      expect(proposal.action, equals(AiCrudAction.search));
      expect(proposal.recordId, equals('PROD-998877'));
    });
  });

  group('4. Confirmation vs Cancellation & Outbox Synchronization', () {
    test('Cancellation does not modify SQLite cache or outbox (Zero side effects)', () async {
      final db = AppDatabase.instance;
      final outbox = OutboxService.instance;

      final initialCached = await db.getCachedEntities('cliente');
      final initialOutbox = await outbox.getPendingOperations();

      expect(initialCached, isEmpty);
      expect(initialOutbox, isEmpty);

      // Generated proposal
      final proposal = AiCommandInterpreter.instance.interpret(
        'crear cliente Prueba Cancelar email cancelar@test.com',
      );
      expect(proposal.isValid, isTrue);

      // Simulating user choosing CANCEL: no execution method is called.
      // Assert state remains completely clean:
      final postCancelCached = await db.getCachedEntities('cliente');
      final postCancelOutbox = await outbox.getPendingOperations();

      expect(postCancelCached, isEmpty);
      expect(postCancelOutbox, isEmpty);
    });

    test('Confirmation executes proposal, writes to SQLite cache and enqueues to outbox', () async {
      final db = AppDatabase.instance;
      final outbox = OutboxService.instance;

      final proposal = AiCommandInterpreter.instance.interpret(
        'crear cliente Roberto Sanchez email roberto@test.com',
      );
      expect(proposal.isValid, isTrue);

      // Simulating user choosing CONFIRM:
      final entityMeta = AiEntityRegistry.instance.findEntity(proposal.entityType)!;
      final result = await entityMeta.execute(proposal);

      expect(result, isNotNull);

      // Check SQLite cached_entities
      final cached = await db.getCachedEntities('cliente');
      expect(cached.length, equals(1));
      expect(cached.first['nombre'], equals('Roberto Sanchez'));
      expect(cached.first['email'], equals('roberto@test.com'));
      expect(cached.first['_syncStatus'], equals('pending_create'));

      // Check outbox_operations
      final pendingOps = await outbox.getPendingOperations();
      expect(pendingOps.length, equals(1));
      expect(pendingOps.first.entity, equals('cliente'));
      expect(pendingOps.first.action.toLowerCase(), equals('create'));
      expect(pendingOps.first.payload!['nombre'], equals('Roberto Sanchez'));
      expect(pendingOps.first.payload!['email'], equals('roberto@test.com'));
    });
  });
}
