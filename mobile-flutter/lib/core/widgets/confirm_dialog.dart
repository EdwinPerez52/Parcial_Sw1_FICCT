import 'package:flutter/material.dart';
import 'package:ventas/core/i18n/app_strings.dart';

class ConfirmDialog {
  static Future<bool> show(
    BuildContext context, {
    String title = AppStrings.confirmDeleteTitle,
    String message = AppStrings.confirmDeleteMessage,
    String confirmText = AppStrings.delete,
    String cancelText = AppStrings.cancel,
  }) async {
    final result = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(title),
        content: Text(message),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: Text(cancelText),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: Text(confirmText),
          ),
        ],
      ),
    );
    return result ?? false;
  }
}
