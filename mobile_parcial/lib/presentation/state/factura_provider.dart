import 'package:flutter/material.dart';
import 'package:ventas/data/models/factura.dart';
import 'package:ventas/data/repositories/factura_repository.dart';

class FacturaProvider extends ChangeNotifier {
  final FacturaRepository _repository = FacturaRepository();

  List<Factura> _items = [];
  bool _isLoading = false;
  bool _isSaving = false;
  String? _errorMessage;
  String _searchQuery = '';

  List<Factura> get items {
    if (_searchQuery.isEmpty) return _items;
    return _items.where((i) => i.displayLabel.toLowerCase().contains(_searchQuery.toLowerCase())).toList();
  }

  bool get isLoading => _isLoading;
  bool get isSaving => _isSaving;
  String? get errorMessage => _errorMessage;
  String get searchQuery => _searchQuery;

  void setSearchQuery(String query) {
    _searchQuery = query.trim();
    notifyListeners();
  }

  Future<void> loadItems() async {
    _isLoading = true;
    _errorMessage = null;
    notifyListeners();
    try {
      _items = await _repository.getAll(search: _searchQuery);
      _isLoading = false;
      notifyListeners();
    } catch (e) {
      _errorMessage = e.toString();
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<Factura?> saveItem(Factura item, {String? id}) async {
    _isSaving = true;
    _errorMessage = null;
    notifyListeners();
    try {
      Factura saved;
      if (id != null && id.isNotEmpty) {
        saved = await _repository.update(id, item);
        final idx = _items.indexWhere((it) => it.id == id);
        if (idx != -1) {
          _items[idx] = saved;
        }
      } else {
        saved = await _repository.create(item);
        _items.insert(0, saved);
      }
      _isSaving = false;
      notifyListeners();
      return saved;
    } catch (e) {
      _errorMessage = e.toString();
      _isSaving = false;
      notifyListeners();
      return null;
    }
  }

  Future<bool> deleteItem(String id) async {
    try {
      await _repository.delete(id);
      _items.removeWhere((it) => it.id == id);
      notifyListeners();
      return true;
    } catch (e) {
      _errorMessage = e.toString();
      notifyListeners();
      return false;
    }
  }
}
