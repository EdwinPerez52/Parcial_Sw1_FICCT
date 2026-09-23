import 'package:uuid/uuid.dart';

enum AiCrudAction {
  create,
  update,
  delete,
  search;

  String get label {
    switch (this) {
      case AiCrudAction.create:
        return 'CREAR';
      case AiCrudAction.update:
        return 'EDITAR';
      case AiCrudAction.delete:
        return 'ELIMINAR';
      case AiCrudAction.search:
        return 'BUSCAR';
    }
  }
}

enum AiProposalSource {
  textLocal,
  voiceLocal,
  ocrLocal,
  remoteAi;

  String get label {
    switch (this) {
      case AiProposalSource.textLocal:
        return 'Texto local';
      case AiProposalSource.voiceLocal:
        return 'Voz local';
      case AiProposalSource.ocrLocal:
        return 'OCR local';
      case AiProposalSource.remoteAi:
        return 'IA remota';
    }
  }
}

class AiCrudProposal {
  final String id;
  final AiCrudAction action;
  final String entityType;
  final String? recordId;
  final Map<String, dynamic> payload;
  final List<String> validationErrors;
  final List<String> warnings;
  final double confidence;
  final AiProposalSource source;
  final String rawCommandSummary;
  final DateTime createdAt;

  AiCrudProposal({
    String? id,
    required this.action,
    required this.entityType,
    this.recordId,
    Map<String, dynamic>? payload,
    List<String>? validationErrors,
    List<String>? warnings,
    this.confidence = 1.0,
    required this.source,
    required this.rawCommandSummary,
    DateTime? createdAt,
  })  : id = id ?? const Uuid().v4(),
        payload = payload ?? {},
        validationErrors = List.unmodifiable(validationErrors ?? []),
        warnings = List.unmodifiable(warnings ?? []),
        createdAt = createdAt ?? DateTime.now();

  bool get isValid => validationErrors.isEmpty;
  bool get isDestructive => action == AiCrudAction.delete;

  AiCrudProposal copyWith({
    String? id,
    AiCrudAction? action,
    String? entityType,
    String? recordId,
    Map<String, dynamic>? payload,
    List<String>? validationErrors,
    List<String>? warnings,
    double? confidence,
    AiProposalSource? source,
    String? rawCommandSummary,
    DateTime? createdAt,
  }) {
    return AiCrudProposal(
      id: id ?? this.id,
      action: action ?? this.action,
      entityType: entityType ?? this.entityType,
      recordId: recordId ?? this.recordId,
      payload: payload ?? Map.from(this.payload),
      validationErrors: validationErrors ?? List.from(this.validationErrors),
      warnings: warnings ?? List.from(this.warnings),
      confidence: confidence ?? this.confidence,
      source: source ?? this.source,
      rawCommandSummary: rawCommandSummary ?? this.rawCommandSummary,
      createdAt: createdAt ?? this.createdAt,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'action': action.name,
        'entityType': entityType,
        'recordId': recordId,
        'payload': payload,
        'validationErrors': validationErrors,
        'warnings': warnings,
        'confidence': confidence,
        'source': source.name,
        'rawCommandSummary': rawCommandSummary,
        'createdAt': createdAt.toIso8601String(),
      };

  factory AiCrudProposal.fromJson(Map<String, dynamic> json) {
    return AiCrudProposal(
      id: json['id']?.toString(),
      action: AiCrudAction.values.firstWhere(
        (e) => e.name == json['action'],
        orElse: () => AiCrudAction.create,
      ),
      entityType: json['entityType']?.toString() ?? '',
      recordId: json['recordId']?.toString(),
      payload: json['payload'] is Map ? Map<String, dynamic>.from(json['payload']) : {},
      validationErrors: (json['validationErrors'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
      warnings: (json['warnings'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
      confidence: (json['confidence'] as num?)?.toDouble() ?? 1.0,
      source: AiProposalSource.values.firstWhere(
        (e) => e.name == json['source'],
        orElse: () => AiProposalSource.textLocal,
      ),
      rawCommandSummary: json['rawCommandSummary']?.toString() ?? '',
      createdAt: json['createdAt'] != null
          ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
          : DateTime.now(),
    );
  }
}
