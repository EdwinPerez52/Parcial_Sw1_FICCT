import 'package:ventas/core/ai/ai_models.dart';
import 'package:ventas/core/ai/ai_entity_registry.dart';

class AiCommandInterpreter {
  static final AiCommandInterpreter instance = AiCommandInterpreter._();

  AiCommandInterpreter._();

  AiCrudProposal interpret(String input, {AiProposalSource source = AiProposalSource.textLocal}) {
    final sanitizedInput = _normalize(input);
    if (sanitizedInput.trim().isEmpty) {
      return AiCrudProposal(
        action: AiCrudAction.search,
        entityType: '',
        validationErrors: ['El comando está vacío'],
        source: source,
        rawCommandSummary: 'Comando vacío',
      );
    }

    // 1. Detect Action
    final action = _detectAction(sanitizedInput);

    // 2. Detect Target Entity
    final entityMeta = _detectEntity(sanitizedInput);
    if (entityMeta == null) {
      return AiCrudProposal(
        action: action,
        entityType: 'Desconocida',
        validationErrors: [
          'No se pudo identificar la entidad (Cliente, Factura, Producto, Pedido).'
        ],
        source: source,
        rawCommandSummary: 'Entidad no reconocida',
      );
    }

    // 3. Extract Record ID (for update, delete, search)
    final recordId = _extractRecordId(sanitizedInput, entityMeta);

    // 4. Extract Attributes & Values
    final extractedData = _extractAttributes(sanitizedInput, entityMeta);

    // 5. Validation Reusing Form Rules
    final validationErrors = <String>[];
    final warnings = <String>[];

    if (action == AiCrudAction.delete || action == AiCrudAction.update) {
      if (recordId == null || recordId.trim().isEmpty) {
        validationErrors.add('Se requiere el ID o identificador del registro para $action.');
      }
    }

    if (action == AiCrudAction.create || action == AiCrudAction.update) {
      final payloadErrors = entityMeta.validatePayload(
        extractedData,
        isCreate: action == AiCrudAction.create,
      );
      validationErrors.addAll(payloadErrors);
    }

    final confidence = validationErrors.isEmpty ? 0.95 : 0.70;
    final summary = 'Comando: ${action.label} ${entityMeta.name} (${extractedData.length} campos)';

    return AiCrudProposal(
      action: action,
      entityType: entityMeta.name,
      recordId: recordId,
      payload: extractedData,
      validationErrors: validationErrors,
      warnings: warnings,
      confidence: confidence,
      source: source,
      rawCommandSummary: summary,
    );
  }

  AiCrudAction _detectAction(String input) {
    final words = input.toLowerCase().split(RegExp(r'\s+'));
    for (final word in words) {
      if (['crear', 'crea', 'nuevo', 'nueva', 'agregar', 'agrega', 'insertar', 'anadir', 'añadir', 'registrar'].contains(word)) {
        return AiCrudAction.create;
      }
      if (['actualizar', 'actualiza', 'editar', 'edita', 'modificar', 'modifica', 'cambiar', 'cambia'].contains(word)) {
        return AiCrudAction.update;
      }
      if (['eliminar', 'elimina', 'borrar', 'borra', 'suprimir', 'quitar', 'quita'].contains(word)) {
        return AiCrudAction.delete;
      }
      if (['buscar', 'busca', 'consultar', 'consulta', 'filtrar', 'filtra', 'ver', 'listar'].contains(word)) {
        return AiCrudAction.search;
      }
    }
    // Default heuristic: if contains 'con' or attribute-like assignments without action verb, assume create
    return AiCrudAction.create;
  }

  AiEntityMeta? _detectEntity(String input) {
    final clean = _normalize(input).toLowerCase();
    for (final entity in AiEntityRegistry.instance.allEntities) {
      final names = [
        entity.name.toLowerCase(),
        entity.tableName.toLowerCase(),
        entity.plural.toLowerCase(),
        ...entity.aliases.map((a) => a.toLowerCase()),
      ];

      for (final n in names) {
        final reg = RegExp('\\b$n\\b', caseSensitive: false);
        if (reg.hasMatch(clean)) {
          return entity;
        }
      }
    }
    return null;
  }

  String? _extractRecordId(String input, AiEntityMeta entityMeta) {
    // Look for patterns like "id: 123", "id 123", "codigo P01", or numbers/UUIDs after action or entity
    final idRegex = RegExp(r'(?:id|codigo|nro|numero|folio|registro)[\s:=]+([a-zA-Z0-9\-_]+)', caseSensitive: false);
    final match = idRegex.firstMatch(input);
    if (match != null) {
      return match.group(1);
    }

    // Direct pattern e.g. "actualizar cliente 123", "eliminar pedido 45"
    final directRegex = RegExp(
      '(?:${entityMeta.name}|${entityMeta.tableName})[\\s:=]+([a-zA-Z0-9\\-_]+)',
      caseSensitive: false,
    );
    final directMatch = directRegex.firstMatch(input);
    if (directMatch != null) {
      final val = directMatch.group(1)!;
      // Make sure val is not the name of an attribute
      final isAttr = entityMeta.attributes.any((a) => a.name.toLowerCase() == val.toLowerCase());
      if (!isAttr) return val;
    }

    return null;
  }

  Map<String, dynamic> _extractAttributes(String input, AiEntityMeta entityMeta) {
    final result = <String, dynamic>{};

    // 1. Explicit key:value or key=value or key value extraction
    for (final attr in entityMeta.attributes) {
      if (attr.isPrimaryKey) continue;

      final keysToMatch = [attr.name.toLowerCase(), ...attr.aliases.map((a) => a.toLowerCase())];

      for (final k in keysToMatch) {
        // Pattern: key[:= ]+value until next known key or end of string
        final pattern = RegExp(
          '\\b$k(?:[\\s:=]+)([^,;]+?)(?=\\s+(?:${_allAttributeKeysPattern(entityMeta)})[\\s:=]|\$|[,;])',
          caseSensitive: false,
        );

        final match = pattern.firstMatch(input);
        if (match != null) {
          final rawVal = match.group(1)?.trim() ?? '';
          if (rawVal.isNotEmpty) {
            result[attr.name] = _castValue(rawVal, attr);
            break;
          }
        }
      }
    }

    // 2. Email heuristic fallback
    if (!result.containsKey('email')) {
      final emailRegex = RegExp(r'([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})');
      final emailMatch = emailRegex.firstMatch(input);
      if (emailMatch != null && entityMeta.attributes.any((a) => a.name == 'email')) {
        result['email'] = emailMatch.group(1);
      }
    }

    // 3. Positional name heuristic for String fields if still missing
    if (entityMeta.name.toLowerCase() == 'cliente' && !result.containsKey('nombre')) {
      final namePattern = RegExp(
        r'(?:cliente|crear cliente|nuevo cliente|nombre|llamado)\s+([A-Za-zÁÉÍÓÚáéíóúñÑ ]+?)(?=\s+(?:email|correo|mail|telefono|con)\b|$)',
        caseSensitive: false,
      );
      final match = namePattern.firstMatch(input);
      if (match != null) {
        final val = match.group(1)?.trim();
        if (val != null && val.isNotEmpty && !val.contains('@')) {
          result['nombre'] = val;
        }
      }
    }

    // 4. Number / Money heuristic for Factura / Pedido if missing
    if (entityMeta.name.toLowerCase() == 'factura') {
      if (!result.containsKey('monto')) {
        final amountMatch = RegExp(r'(?:\$|bs|monto|total|por)?\s*(\d+(?:\.\d{1,2})?)\s*(?:bs|\$|usd)?', caseSensitive: false).firstMatch(input);
        if (amountMatch != null) {
          final amt = double.tryParse(amountMatch.group(1)!);
          if (amt != null && amt > 0) result['monto'] = amt;
        }
      }
      if (!result.containsKey('numeroFactura')) {
        final numMatch = RegExp(r'(?:factura|numero|nro|folio)?\s*([A-Z]{0,3}-?\d{2,10})', caseSensitive: false).firstMatch(input);
        if (numMatch != null) {
          result['numeroFactura'] = numMatch.group(1);
        }
      }
    }

    return result;
  }

  String _allAttributeKeysPattern(AiEntityMeta entity) {
    final keys = <String>{};
    for (final a in entity.attributes) {
      keys.add(a.name.toLowerCase());
      for (final alias in a.aliases) {
        keys.add(alias.toLowerCase());
      }
    }
    return keys.join('|');
  }

  dynamic _castValue(String raw, AiAttributeMeta attr) {
    var clean = raw.replaceAll(RegExp(r'["\x27]'), '').trim();
    switch (attr.type.toLowerCase()) {
      case 'integer':
      case 'int':
      case 'long':
        final parsed = int.tryParse(clean.replaceAll(RegExp(r'[^\d-]'), ''));
        return parsed ?? clean;
      case 'decimal':
      case 'double':
      case 'float':
      case 'number':
        final cleanedNum = clean.replaceAll(RegExp(r'[^\d.-]'), '');
        final parsed = double.tryParse(cleanedNum);
        return parsed ?? clean;
      case 'boolean':
      case 'bool':
        final lower = clean.toLowerCase();
        return lower == 'true' || lower == '1' || lower == 'si';
      default:
        if (attr.enumValues != null && attr.enumValues!.isNotEmpty) {
          final upper = clean.toUpperCase();
          if (attr.enumValues!.contains(upper)) {
            return upper;
          }
        }
        return clean;
    }
  }

  String _normalize(String text) {
    return text
        .replaceAll('á', 'a')
        .replaceAll('é', 'e')
        .replaceAll('í', 'i')
        .replaceAll('ó', 'o')
        .replaceAll('ú', 'u')
        .replaceAll('Á', 'A')
        .replaceAll('É', 'E')
        .replaceAll('Í', 'I')
        .replaceAll('Ó', 'O')
        .replaceAll('Ú', 'U');
  }
}
