import 'dart:async';

import 'package:flutter/services.dart';

typedef ResultCallback = void Function(String data);

class BlufiPlugin {
  final MethodChannel _channel = const MethodChannel('blufi_plugin');
  final EventChannel _eventChannel = EventChannel('blufi_plugin/state');

  BlufiPlugin._() {
    _channel.setMethodCallHandler((MethodCall call) {
      return;
    });

    _eventChannel
        .receiveBroadcastStream()
        .listen(speechResultsHandler, onError: speechResultErrorHandler);
  }

  ResultCallback _resultSuccessCallback;
  ResultCallback _resultErrorCallback;

  static BlufiPlugin _instance = new BlufiPlugin._();
  static BlufiPlugin get instance => _instance;

  void onMessageReceived(
      {ResultCallback successCallback, ResultCallback errorCallback}) {
    _resultSuccessCallback = successCallback;
    _resultErrorCallback = errorCallback;
  }

  Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  Future scanDeviceInfo({String filterString}) async {
    await _channel.invokeMapMethod(
        'scanDeviceInfo', <String, dynamic>{'filter': filterString});
  }

  Future stopScan() async {
    await _channel.invokeMapMethod('stopScan');
  }

  Future connectPeripheral({String peripheralAddress}) async {
    await _channel.invokeMapMethod('connectPeripheral',
        <String, dynamic>{'peripheral': peripheralAddress});
  }

  Future requestCloseConnection() async {
    await _channel.invokeMapMethod('requestCloseConnection');
  }

  Future negotiateSecurity() async {
    await _channel.invokeMapMethod('negotiateSecurity');
  }

  Future requestDeviceVersion() async {
    await _channel.invokeMapMethod('requestDeviceVersion');
  }

  Future configProvision({String username, String password}) async {
    await _channel.invokeMapMethod('configProvision',
        <String, dynamic>{'username': username, 'password': password});
  }

  Future requestDeviceStatus() async {
    await _channel.invokeMapMethod('requestDeviceStatus');
  }

  Future requestDeviceScan() async {
    await _channel.invokeMapMethod('requestDeviceScan');
  }

  Future postCustomData(String dataStr) async {
    await _channel.invokeMapMethod(
        'postCustomData', <String, dynamic>{'custom_data': dataStr});
  }

  speechResultsHandler(dynamic event) {
    if (_resultSuccessCallback != null) _resultSuccessCallback(event);
  }

  speechResultErrorHandler(dynamic error) {
    if (_resultErrorCallback != null) _resultErrorCallback(error);
  }
}
