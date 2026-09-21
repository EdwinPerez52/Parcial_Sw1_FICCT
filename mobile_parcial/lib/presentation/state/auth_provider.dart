import 'package:flutter/material.dart';
import 'package:ventas/core/api/api_client.dart';
import 'package:ventas/data/models/auth_models.dart';
import 'package:ventas/data/repositories/auth_repository.dart';

class AuthProvider extends ChangeNotifier {
  final AuthRepository _repository = AuthRepository();

  UserProfile? _user;
  bool _isLoading = false;
  String? _errorMessage;

  UserProfile? get user => _user;
  bool get isLoading => _isLoading;
  String? get errorMessage => _errorMessage;
  bool get isAuthenticated => ApiClient.instance.isAuthenticated;

  Future<void> init() async {
    await ApiClient.instance.init();
    if (isAuthenticated) {
      _user = await _repository.getCachedUser();
      notifyListeners();
      try {
        _user = await _repository.me();
        notifyListeners();
      } catch (_) {}
    }
  }

  Future<bool> login(String email, String password) async {
    _setLoading(true);
    try {
      final res = await _repository.login(email, password);
      _user = res.user;
      _errorMessage = null;
      _setLoading(false);
      return true;
    } catch (e) {
      _errorMessage = e.toString();
      _setLoading(false);
      return false;
    }
  }

  Future<bool> register(String fullName, String email, String password) async {
    _setLoading(true);
    try {
      final res = await _repository.register(fullName, email, password);
      _user = res.user;
      _errorMessage = null;
      _setLoading(false);
      return true;
    } catch (e) {
      _errorMessage = e.toString();
      _setLoading(false);
      return false;
    }
  }

  Future<void> logout() async {
    await _repository.logout();
    _user = null;
    notifyListeners();
  }

  void _setLoading(bool value) {
    _isLoading = value;
    notifyListeners();
  }
}
