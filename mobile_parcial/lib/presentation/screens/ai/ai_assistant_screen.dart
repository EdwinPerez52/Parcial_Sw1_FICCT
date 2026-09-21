import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:ventas/core/ai/ai_command_interpreter.dart';
import 'package:ventas/core/ai/ai_models.dart';
import 'package:ventas/core/ai/local_ocr_service.dart';
import 'package:ventas/core/ai/remote_ai_service.dart';
import 'package:ventas/core/ai/speech_recognition_service.dart';
import 'package:ventas/core/sync/sync_service.dart';
import 'package:ventas/presentation/screens/ai/ai_proposal_preview_dialog.dart';

class AiAssistantScreen extends StatefulWidget {
  const AiAssistantScreen({super.key});

  @override
  State<AiAssistantScreen> createState() => _AiAssistantScreenState();
}

class _AiAssistantScreenState extends State<AiAssistantScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  final TextEditingController _textController = TextEditingController();
  final SpeechRecognitionService _speech = SpeechRecognitionService.instance;
  final LocalOcrService _ocr = LocalOcrService.instance;
  final RemoteAiService _remoteAi = RemoteAiService.instance;
  final SyncService _sync = SyncService.instance;

  XFile? _selectedImage;
  ImageValidationResult? _imageValidation;
  bool _isProcessingImage = false;
  bool _useRemoteWhenAvailable = false;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 3, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    _textController.dispose();
    _speech.stopListening();
    super.dispose();
  }

  void _submitTextCommand(String command) {
    if (command.trim().isEmpty) return;
    final proposal = AiCommandInterpreter.instance.interpret(
      command.trim(),
      source: AiProposalSource.textLocal,
    );
    _showProposal(proposal);
  }

  Future<void> _toggleVoiceListening() async {
    if (_speech.isListening) {
      await _speech.stopListening();
    } else {
      await _speech.startListening(
        onResult: (proposal) {
          _showProposal(proposal);
        },
      );
    }
  }

  Future<void> _pickImage(ImageSource source) async {
    final file = await _ocr.pickImage(source);
    if (file == null) return;

    final validation = await _ocr.validateImageFile(file);
    setState(() {
      _selectedImage = file;
      _imageValidation = validation;
    });

    if (validation.isValid) {
      _processImage();
    }
  }

  Future<void> _processImage() async {
    if (_selectedImage == null) return;
    setState(() => _isProcessingImage = true);

    try {
      AiCrudProposal proposal;
      if (_useRemoteWhenAvailable && _remoteAi.canUseRemoteAi) {
        proposal = await _remoteAi.analyzeVisualComplex(
          imageFile: _selectedImage!,
          prompt: _textController.text.trim().isNotEmpty ? _textController.text.trim() : null,
        );
      } else {
        proposal = await _ocr.extractFromImage(_selectedImage!);
      }

      if (mounted) {
        setState(() => _isProcessingImage = false);
        _showProposal(proposal);
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isProcessingImage = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error al procesar imagen: $e'), backgroundColor: Colors.red),
        );
      }
    }
  }

  void _showProposal(AiCrudProposal proposal) {
    AiProposalPreviewDialog.show(context, proposal);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Asistente IA Móvil'),
        bottom: TabBar(
          controller: _tabController,
          indicatorColor: Colors.white,
          tabs: const [
            Tab(icon: Icon(Icons.text_fields), text: 'Texto'),
            Tab(icon: Icon(Icons.mic), text: 'Voz'),
            Tab(icon: Icon(Icons.camera_alt), text: 'Fotografía'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildTextTab(),
          _buildVoiceTab(),
          _buildPhotoTab(),
        ],
      ),
    );
  }

  // =========================================================================
  // 1. TEXT TAB
  // =========================================================================
  Widget _buildTextTab() {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          elevation: 2,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
              const Text(
                'Comando en Lenguaje Natural',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              const SizedBox(height: 6),
              Text(
                'El intérprete local procesará tu comando sin red para estructurar la propuesta.',
                style: TextStyle(fontSize: 13, color: Colors.grey.shade700),
              ),
              const SizedBox(height: 14),
              TextField(
                controller: _textController,
                maxLines: 3,
                decoration: InputDecoration(
                  hintText: 'Ej. Crear cliente Carlos Gómez email carlos@empresa.com',
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(10)),
                  filled: true,
                  fillColor: Colors.grey.shade50,
                ),
              ),
              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: () => _submitTextCommand(_textController.text),
                icon: const Icon(Icons.auto_awesome),
                label: const Text('Interpretar y Proponer'),
                style: ElevatedButton.styleFrom(
                  backgroundColor: const Color(0xFF1E3A8A),
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                ),
              ),
            ],
          ),
        ),
      ),
        const SizedBox(height: 16),
        const Text(
          'Comandos sugeridos para probar:',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14, color: Color(0xFF1E3A8A)),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            _buildQuickChip('Crear cliente Juan Pérez email juan@correo.com'),
            _buildQuickChip('Nuevo producto código PROD-01 precio 49.90 stock 100'),
            _buildQuickChip('Crear factura numeroFactura F-100 monto 350.00'),
            _buildQuickChip('Crear pedido numero PED-50 total 180.00 estado NUEVO'),
            _buildQuickChip('Actualizar producto PROD-01 precio 55.00'),
            _buildQuickChip('Buscar cliente Juan'),
            _buildQuickChip('Eliminar pedido 1'),
          ],
        ),
      ],
    );
  }

  Widget _buildQuickChip(String text) {
    return ActionChip(
      label: Text(text, style: const TextStyle(fontSize: 12)),
      onPressed: () {
        _textController.text = text;
        _submitTextCommand(text);
      },
      backgroundColor: Colors.blue.shade50,
    );
  }

  // =========================================================================
  // 2. VOICE TAB
  // =========================================================================
  Widget _buildVoiceTab() {
    return AnimatedBuilder(
      animation: _speech,
      builder: (context, _) {
        final isListening = _speech.isListening;
        return Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Text(
                isListening
                    ? 'Escuchando tu comando...'
                    : 'Toca el micrófono y habla',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.bold,
                  color: isListening ? Colors.red.shade700 : const Color(0xFF1E3A8A),
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Usa el reconocimiento disponible en tu dispositivo. La transcripción se procesa mediante el intérprete local offline.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
              ),
              const SizedBox(height: 40),

              // Animated Mic Button
              GestureDetector(
                onTap: _toggleVoiceListening,
                child: AnimatedContainer(
                  duration: const Duration(milliseconds: 300),
                  width: isListening ? 110 : 90,
                  height: isListening ? 110 : 90,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: isListening ? Colors.red : const Color(0xFF2563EB),
                    boxShadow: [
                      BoxShadow(
                        color: (isListening ? Colors.red : const Color(0xFF2563EB))
                            .withOpacity(0.4),
                        blurRadius: isListening ? 20 : 10,
                        spreadRadius: isListening ? 6 : 2,
                      ),
                    ],
                  ),
                  child: Icon(
                    isListening ? Icons.stop : Icons.mic,
                    size: 46,
                    color: Colors.white,
                  ),
                ),
              ),
              const SizedBox(height: 24),

              if (_speech.lastRecognizedWords.isNotEmpty)
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.grey.shade100,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(color: Colors.grey.shade300),
                  ),
                  child: Text(
                    '"${_speech.lastRecognizedWords}"',
                    style: const TextStyle(fontStyle: FontStyle.italic, fontSize: 14),
                    textAlign: TextAlign.center,
                  ),
                ),

              if (_speech.errorMessage != null)
                Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: Text(
                    _speech.errorMessage!,
                    style: const TextStyle(color: Colors.red, fontSize: 12),
                    textAlign: TextAlign.center,
                  ),
                ),
            ],
          ),
        );
      },
    );
  }

  // =========================================================================
  // 3. PHOTO / OCR TAB
  // =========================================================================
  Widget _buildPhotoTab() {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        Card(
          elevation: 2,
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
              const Text(
                'Captura y OCR Local',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
              ),
              const SizedBox(height: 6),
              Text(
                'Toma una fotografía o selecciona un comprobante. El OCR local extraerá textos, códigos y valores para proponer el formulario o búsqueda.',
                style: TextStyle(fontSize: 13, color: Colors.grey.shade700),
              ),
              const SizedBox(height: 16),

              Row(
                children: [
                  Expanded(
                    child: ElevatedButton.icon(
                      onPressed: () => _pickImage(ImageSource.camera),
                      icon: const Icon(Icons.camera_alt),
                      label: const Text('Cámara'),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: const Color(0xFF1E3A8A),
                        foregroundColor: Colors.white,
                        padding: const EdgeInsets.symmetric(vertical: 12),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: () => _pickImage(ImageSource.gallery),
                      icon: const Icon(Icons.photo_library),
                      label: const Text('Galería'),
                      style: OutlinedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 12),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),

        const SizedBox(height: 12),

        // Remote AI Switch (Optional when online)
        SwitchListTile(
          title: const Text('Análisis asistido con IA remota', style: TextStyle(fontSize: 14)),
          subtitle: Text(
            _sync.isOnline
                ? 'Conectado al backend (análisis visual complejo disponible)'
                : 'Sin conexión: opera en modo OCR local 100% offline',
            style: TextStyle(fontSize: 12, color: _sync.isOnline ? Colors.green.shade800 : Colors.grey),
          ),
          value: _useRemoteWhenAvailable && _sync.isOnline,
          onChanged: _sync.isOnline ? (v) => setState(() => _useRemoteWhenAvailable = v) : null,
        ),

        const SizedBox(height: 12),

        // Selected Image Preview & Action
        if (_selectedImage != null)
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                Row(
                  children: [
                    const Icon(Icons.image, color: Color(0xFF1E3A8A)),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        _selectedImage!.name,
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    if (_imageValidation != null && _imageValidation!.isValid)
                      Chip(
                        label: Text(
                          '${_imageValidation!.mimeType} • ${(_imageValidation!.fileSizeBytes / 1024).toStringAsFixed(0)} KB',
                          style: const TextStyle(fontSize: 11),
                        ),
                        backgroundColor: Colors.green.shade50,
                      )
                    else if (_imageValidation != null)
                      Chip(
                        label: const Text('Inválido', style: TextStyle(fontSize: 11, color: Colors.red)),
                        backgroundColor: Colors.red.shade50,
                      ),
                  ],
                ),
                const SizedBox(height: 12),
                ElevatedButton.icon(
                  onPressed: _isProcessingImage ? null : _processImage,
                  icon: _isProcessingImage
                      ? const SizedBox(
                          height: 16,
                          width: 16,
                          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                        )
                      : const Icon(Icons.document_scanner),
                  label: Text(_isProcessingImage ? 'Procesando OCR...' : 'Extraer Datos con OCR'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.green.shade700,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 12),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
