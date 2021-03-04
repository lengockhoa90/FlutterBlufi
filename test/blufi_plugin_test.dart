import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:blufi_plugin/blufi_plugin.dart';

void main() {
  const MethodChannel channel = MethodChannel('blufi_plugin');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });


}
