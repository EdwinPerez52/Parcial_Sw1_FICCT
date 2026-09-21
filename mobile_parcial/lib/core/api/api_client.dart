import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:ventas/core/config/app_config.dart';
import 'package:ventas/core/api/api_exception.dart';
import 'package:ventas/core/storage/secure_storage_service.dart';

class ApiClient {
  static final ApiClient instance = ApiClient._();
  ApiClient._();

  final SecureStorageService _secureStorage = SecureStorageService.instance;

  String? _accessToken;
  String? _refreshToken;

  Future<void> init() async {
    try {
      _accessToken = await _secureStorage.read('auth_access_token');
      _refreshToken = await _secureStorage.read('auth_refresh_token');
    } catch (_) {}
  }

  bool get isAuthenticated => _accessToken != null && _accessToken!.isNotEmpty;
  String? get token => _accessToken;

  Future<void> saveTokens({required String accessToken, required String refreshToken}) async {
    _accessToken = accessToken;
    _refreshToken = refreshToken;
    await _secureStorage.write('auth_access_token', accessToken);
    await _secureStorage.write('auth_refresh_token', refreshToken);
  }

  Future<void> clearTokens() async {
    _accessToken = null;
    _refreshToken = null;
    await _secureStorage.delete('auth_access_token');
    await _secureStorage.delete('auth_refresh_token');
  }

  Future<Map<String, String>> _headers([Map<String, String>? extra]) async {
    final headers = <String, String>{
      'Content-Type': 'application/json; charset=utf-8',
      'Accept': 'application/json',
    };
    if (_accessToken != null && _accessToken!.isNotEmpty) {
      headers['Authorization'] = 'Bearer $_accessToken';
    }
    if (extra != null) {
      headers.addAll(extra);
    }
    return headers;
  }

  Future<dynamic> get(String path, {Map<String, String>? queryParams, Map<String, String>? headers}) async {
    final baseUrl = await AppConfig.getBaseUrl();
    var uri = Uri.parse('$baseUrl$path');
    if (queryParams != null && queryParams.isNotEmpty) {
      uri = uri.replace(queryParameters: queryParams);
    }
    final response = await http.get(uri, headers: await _headers(headers));
    return _handleResponse(response, () => get(path, queryParams: queryParams, headers: headers));
  }

  Future<dynamic> post(String path, {dynamic body, Map<String, String>? headers}) async {
    final baseUrl = await AppConfig.getBaseUrl();
    final uri = Uri.parse('$baseUrl$path');
    final response = await http.post(
      uri,
      headers: await _headers(headers),
      body: body != null ? jsonEncode(body) : null,
    );
    return _handleResponse(response, () => post(path, body: body, headers: headers));
  }

  Future<dynamic> put(String path, {dynamic body, Map<String, String>? headers}) async {
    final baseUrl = await AppConfig.getBaseUrl();
    final uri = Uri.parse('$baseUrl$path');
    final response = await http.put(
      uri,
      headers: await _headers(headers),
      body: body != null ? jsonEncode(body) : null,
    );
    return _handleResponse(response, () => put(path, body: body, headers: headers));
  }

  Future<void> delete(String path, {Map<String, String>? headers}) async {
    final baseUrl = await AppConfig.getBaseUrl();
    final uri = Uri.parse('$baseUrl$path');
    final response = await http.delete(uri, headers: await _headers(headers));
    if (response.statusCode == 204 || response.statusCode == 200) {
      return;
    }
    await _handleResponse(response, () => delete(path, headers: headers));
  }

  Future<dynamic> _handleResponse(http.Response response, Future<dynamic> Function() retry) async {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      if (response.body.isEmpty) return null;
      return jsonDecode(utf8.decode(response.bodyBytes));
    }

    if (response.statusCode == 401 && _refreshToken != null) {
      final refreshed = await _tryRefreshToken();
      if (refreshed) {
        return retry();
      }
    }

    String message = 'Error ${response.statusCode}';
    Map<String, dynamic>? errors;
    try {
      final body = jsonDecode(utf8.decode(response.bodyBytes));
      if (body is Map<String, dynamic>) {
        message = body['message'] ?? body['error'] ?? body['detail'] ?? message;
        if (body['validationErrors'] is Map<String, dynamic>) {
          errors = body['validationErrors'];
        }
      }
    } catch (_) {}

    throw ApiException(statusCode: response.statusCode, message: message, errors: errors);
  }

  Future<bool> _tryRefreshToken() async {
    if (_refreshToken == null) return false;
    try {
      final baseUrl = await AppConfig.getBaseUrl();
      final uri = Uri.parse('$baseUrl/api/auth/refresh');
      final res = await http.post(
        uri,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'refreshToken': _refreshToken}),
      );
      if (res.statusCode == 200) {
        final data = jsonDecode(utf8.decode(res.bodyBytes));
        if (data is Map<String, dynamic> && data['accessToken'] != null) {
          await saveTokens(
            accessToken: data['accessToken'],
            refreshToken: data['refreshToken'] ?? _refreshToken!,
          );
          return true;
        }
      }
    } catch (_) {}
    await clearTokens();
    return false;
  }
}
