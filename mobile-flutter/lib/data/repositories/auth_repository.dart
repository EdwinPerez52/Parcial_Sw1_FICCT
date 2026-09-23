import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ventas/core/api/api_client.dart';
import 'package:ventas/data/models/auth_models.dart';

class AuthRepository {
  final ApiClient _client = ApiClient.instance;

  Future<AuthResponse> login(String email, String password) async {
    final data = await _client.post('/api/auth/login', body: {
      'email': email,
      'password': password,
    });
    final response = AuthResponse.fromJson(data);
    await _client.saveTokens(
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
    );
    await _saveUser(response.user);
    return response;
  }

  Future<AuthResponse> register(String fullName, String email, String password) async {
    final data = await _client.post('/api/auth/register', body: {
      'fullName': fullName,
      'email': email,
      'password': password,
    });
    final response = AuthResponse.fromJson(data);
    await _client.saveTokens(
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
    );
    await _saveUser(response.user);
    return response;
  }

  Future<UserProfile> me() async {
    final data = await _client.get('/api/auth/me');
    final user = UserProfile.fromJson(data);
    await _saveUser(user);
    return user;
  }

  Future<void> logout() async {
    await _client.clearTokens();
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('auth_user_profile');
  }

  Future<UserProfile?> getCachedUser() async {
    final prefs = await SharedPreferences.getInstance();
    final str = prefs.getString('auth_user_profile');
    if (str != null) {
      try {
        return UserProfile.fromJson(jsonDecode(str));
      } catch (_) {}
    }
    return null;
  }

  Future<void> _saveUser(UserProfile user) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('auth_user_profile', jsonEncode(user.toJson()));
  }
}
