import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SecureStorageService {
  static final SecureStorageService instance = SecureStorageService._();
  SecureStorageService._();

  final FlutterSecureStorage _secureStorage = const FlutterSecureStorage();
  final Map<String, String> _memoryFallback = {};

  Future<void> write(String key, String value) async {
    _memoryFallback[key] = value;
    try {
      await _secureStorage.write(key: key, value: value);
    } catch (_) {
      try {
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('sec_$key', value);
      } catch (_) {}
    }
  }

  Future<String?> read(String key) async {
    try {
      final val = await _secureStorage.read(key: key);
      if (val != null && val.isNotEmpty) return val;
    } catch (_) {}
    if (_memoryFallback.containsKey(key)) {
      return _memoryFallback[key];
    }
    try {
      final prefs = await SharedPreferences.getInstance();
      return prefs.getString('sec_$key');
    } catch (_) {
      return null;
    }
  }

  Future<void> delete(String key) async {
    _memoryFallback.remove(key);
    try {
      await _secureStorage.delete(key: key);
    } catch (_) {}
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove('sec_$key');
    } catch (_) {}
  }
}
