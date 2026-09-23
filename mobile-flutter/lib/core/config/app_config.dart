import 'package:shared_preferences/shared_preferences.dart';

class AppConfig {
  static const String defaultApiUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080',
  );

  static const String _storageKey = 'collab_modeler_api_base_url';

  static Future<String> getBaseUrl() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final saved = prefs.getString(_storageKey);
      if (saved != null && saved.trim().isNotEmpty) {
        return saved.trim().replaceAll(RegExp(r'/+$'), '');
      }
    } catch (_) {}
    return defaultApiUrl.replaceAll(RegExp(r'/+$'), '');
  }

  static Future<void> setBaseUrl(String url) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_storageKey, url.trim().replaceAll(RegExp(r'/+$'), ''));
  }

  static Future<void> resetBaseUrl() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_storageKey);
  }
}
