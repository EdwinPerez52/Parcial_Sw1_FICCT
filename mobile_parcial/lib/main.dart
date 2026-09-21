import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:ventas/core/theme/app_theme.dart';
import 'package:ventas/core/database/app_database.dart';
import 'package:ventas/core/sync/sync_service.dart';
import 'package:ventas/presentation/state/auth_provider.dart';
import 'package:ventas/presentation/screens/auth/login_screen.dart';
import 'package:ventas/presentation/screens/home/home_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppDatabase.instance.database;
  final authProvider = AuthProvider();
  await authProvider.init();
  await SyncService.instance.init();
  runApp(CollabModelerApp(authProvider: authProvider));
}

class CollabModelerApp extends StatelessWidget {
  final AuthProvider authProvider;
  const CollabModelerApp({super.key, required this.authProvider});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Ventas Móvil',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [
        Locale('es', 'ES'),
        Locale('es', ''),
        Locale('en', ''),
      ],
      home: ListenableBuilder(
        listenable: authProvider,
        builder: (ctx, _) {
          if (authProvider.isAuthenticated) {
            return HomeScreen(authProvider: authProvider);
          }
          return LoginScreen(authProvider: authProvider);
        },
      ),
    );
  }
}
