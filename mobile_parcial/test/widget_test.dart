import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:ventas/main.dart';
import 'package:ventas/presentation/state/auth_provider.dart';

void main() {
  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  testWidgets('CollabModelerApp launches and displays login or home screen', (WidgetTester tester) async {
    final authProvider = AuthProvider();
    await tester.pumpWidget(CollabModelerApp(authProvider: authProvider));
    expect(find.byType(CollabModelerApp), findsOneWidget);
  });
}
