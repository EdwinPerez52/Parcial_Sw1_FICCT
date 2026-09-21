import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:speech_to_text/speech_to_text.dart' as stt;
import 'package:ventas/core/ai/ai_command_interpreter.dart';
import 'package:ventas/core/ai/ai_models.dart';

enum SpeechRecognitionStatus {
  uninitialized,
  idle,
  listening,
  processing,
  error,
}

class SpeechRecognitionService extends ChangeNotifier {
  static final SpeechRecognitionService instance = SpeechRecognitionService._();

  final stt.SpeechToText _speech = stt.SpeechToText();
  SpeechRecognitionStatus _status = SpeechRecognitionStatus.uninitialized;
  bool _isAvailable = false;
  String _lastRecognizedWords = '';
  String? _errorMessage;
  double _soundLevel = 0.0;

  SpeechRecognitionService._();

  SpeechRecognitionStatus get status => _status;
  bool get isListening => _status == SpeechRecognitionStatus.listening;
  bool get isAvailable => _isAvailable;
  String get lastRecognizedWords => _lastRecognizedWords;
  String? get errorMessage => _errorMessage;
  double get soundLevel => _soundLevel;

  Future<bool> initialize() async {
    if (_status != SpeechRecognitionStatus.uninitialized && _isAvailable) {
      return true;
    }

    try {
      _isAvailable = await _speech.initialize(
        onError: (val) {
          _status = SpeechRecognitionStatus.error;
          _errorMessage = val.errorMsg;
          notifyListeners();
        },
        onStatus: (val) {
          if (val == 'listening') {
            _status = SpeechRecognitionStatus.listening;
          } else if (val == 'notListening' || val == 'done') {
            if (_status == SpeechRecognitionStatus.listening) {
              _status = SpeechRecognitionStatus.idle;
            }
          }
          notifyListeners();
        },
      );

      _status = _isAvailable ? SpeechRecognitionStatus.idle : SpeechRecognitionStatus.error;
      if (!_isAvailable) {
        _errorMessage = 'Reconocimiento de voz no disponible en este dispositivo';
      }
      notifyListeners();
      return _isAvailable;
    } catch (e) {
      _isAvailable = false;
      _status = SpeechRecognitionStatus.error;
      _errorMessage = 'Error al inicializar micrófono: ${e.toString()}';
      notifyListeners();
      return false;
    }
  }

  Future<void> startListening({
    required Function(AiCrudProposal proposal) onResult,
    String localeId = 'es_ES',
  }) async {
    _lastRecognizedWords = '';
    _errorMessage = null;

    final ready = await initialize();
    if (!ready) {
      _status = SpeechRecognitionStatus.error;
      notifyListeners();
      return;
    }

    _status = SpeechRecognitionStatus.listening;
    notifyListeners();

    try {
      await _speech.listen(
        onResult: (result) {
          _lastRecognizedWords = result.recognizedWords;
          notifyListeners();

          if (result.finalResult) {
            _status = SpeechRecognitionStatus.processing;
            notifyListeners();

            final proposal = AiCommandInterpreter.instance.interpret(
              _lastRecognizedWords,
              source: AiProposalSource.voiceLocal,
            );

            _status = SpeechRecognitionStatus.idle;
            notifyListeners();
            onResult(proposal);
          }
        },
        listenFor: const Duration(seconds: 20),
        pauseFor: const Duration(seconds: 3),
        localeId: localeId,
        onSoundLevelChange: (level) {
          _soundLevel = level;
          notifyListeners();
        },
        cancelOnError: true,
      );
    } catch (e) {
      _status = SpeechRecognitionStatus.error;
      _errorMessage = 'Error al iniciar escucha: $e';
      notifyListeners();
    }
  }

  Future<void> stopListening() async {
    if (_speech.isListening) {
      await _speech.stop();
    }
    _status = SpeechRecognitionStatus.idle;
    notifyListeners();
  }

  Future<void> cancelListening() async {
    if (_speech.isListening) {
      await _speech.cancel();
    }
    _status = SpeechRecognitionStatus.idle;
    notifyListeners();
  }

  /// Process direct text input as if transcribed from voice (useful for headless testing or voice fallbacks)
  AiCrudProposal processVoiceTranscription(String text) {
    _lastRecognizedWords = text;
    // Note: Never log sensitive transcripts directly
    return AiCommandInterpreter.instance.interpret(
      text,
      source: AiProposalSource.voiceLocal,
    );
  }
}
