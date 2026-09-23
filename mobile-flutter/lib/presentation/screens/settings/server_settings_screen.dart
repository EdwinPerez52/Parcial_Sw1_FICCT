import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:ventas/core/config/app_config.dart';
import 'package:ventas/core/i18n/app_strings.dart';

class ServerSettingsScreen extends StatefulWidget {
  const ServerSettingsScreen({super.key});

  @override
  State<ServerSettingsScreen> createState() => _ServerSettingsScreenState();
}

class _ServerSettingsScreenState extends State<ServerSettingsScreen> {
  final _controller = TextEditingController();
  bool _testing = false;
  String? _testResult;
  bool? _testSuccess;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final url = await AppConfig.getBaseUrl();
    _controller.text = url;
    if (mounted) setState(() {});
  }

  Future<void> _test() async {
    setState(() {
      _testing = true;
      _testResult = null;
      _testSuccess = null;
    });
    final raw = _controller.text.trim().replaceAll(RegExp(r'/+$'), '');
    try {
      final uri = Uri.parse('$raw/actuator/health');
      final res = await http.get(uri).timeout(const Duration(seconds: 4));
      if (res.statusCode == 200) {
        setState(() {
          _testSuccess = true;
          _testResult = AppStrings.connectionSuccess;
        });
      } else {
        setState(() {
          _testSuccess = false;
          _testResult = 'Respuesta inesperada: ${res.statusCode}';
        });
      }
    } catch (e) {
      setState(() {
        _testSuccess = false;
        _testResult = '${AppStrings.connectionFailed}: $e';
      });
    } finally {
      setState(() => _testing = false);
    }
  }

  Future<void> _save() async {
    final raw = _controller.text.trim().replaceAll(RegExp(r'/+$'), '');
    if (raw.isNotEmpty) {
      await AppConfig.setBaseUrl(raw);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Configuración guardada exitosamente')),
        );
        Navigator.of(context).pop();
      }
    }
  }

  Future<void> _reset() async {
    await AppConfig.resetBaseUrl();
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text(AppStrings.serverSettings)),
      body: ListView(
        padding: const EdgeInsets.all(20.0),
        children: [
          Card(
            child: Padding(
              padding: const EdgeInsets.all(18.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  const Text(
                    'Configuración de Red del Backend',
                    style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Por defecto conecta a http://localhost:8080 configurado con --dart-define. Puedes cambiar la URL para apuntar a la IP de tu PC o a un servidor remoto.',
                    style: TextStyle(fontSize: 13, color: Colors.grey),
                  ),
                  const SizedBox(height: 18),
                  TextFormField(
                    controller: _controller,
                    decoration: const InputDecoration(
                      labelText: AppStrings.serverUrl,
                      hintText: 'http://localhost:8080',
                      prefixIcon: Icon(Icons.dns_outlined),
                    ),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: _testing ? null : _test,
                          icon: _testing
                              ? const SizedBox(
                                  width: 16,
                                  height: 16,
                                  child: CircularProgressIndicator(strokeWidth: 2),
                                )
                              : const Icon(Icons.wifi_find),
                          label: const Text(AppStrings.testConnection),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: ElevatedButton.icon(
                          onPressed: _save,
                          icon: const Icon(Icons.save),
                          label: const Text(AppStrings.save),
                        ),
                      ),
                    ],
                  ),
                  if (_testResult != null) ...[
                    const SizedBox(height: 14),
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: _testSuccess == true
                            ? Colors.green.shade50
                            : Colors.red.shade50,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(
                          color: _testSuccess == true
                              ? Colors.green.shade300
                              : Colors.red.shade300,
                        ),
                      ),
                      child: Text(
                        _testResult!,
                        style: TextStyle(
                          color: _testSuccess == true
                              ? Colors.green.shade900
                              : Colors.red.shade900,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ],
                  const SizedBox(height: 12),
                  TextButton(
                    onPressed: _reset,
                    child: const Text('Restablecer a valor predeterminado'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          Card(
            color: Colors.blue.shade50,
            child: const Padding(
              padding: EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.phone_android, color: Color(0xFF1E3A8A)),
                      SizedBox(width: 8),
                      Text(
                        'Conexión con Android Físico por USB',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          color: Color(0xFF1E3A8A),
                        ),
                      ),
                    ],
                  ),
                  SizedBox(height: 8),
                  Text(
                    'Para que tu teléfono Samsung conectado por USB alcance el backend en localhost:8080 sin configurar Wi-Fi, ejecuta en tu terminal:\n\n'
                    '  adb reverse tcp:8080 tcp:8080\n\n'
                    'Si usas conexión Wi-Fi, ingresa la IP local de tu computadora (por ejemplo: http://192.168.1.50:8080).',
                    style: TextStyle(fontSize: 13, height: 1.4),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
