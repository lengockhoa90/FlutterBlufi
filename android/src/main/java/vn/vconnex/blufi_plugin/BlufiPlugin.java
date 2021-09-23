package vn.vconnex.blufi_plugin;

import android.Manifest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;


import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import vn.vconnex.blufi_plugin.constants.BlufiConstants;
import vn.vconnex.blufi_plugin.params.BlufiConfigureParams;
import vn.vconnex.blufi_plugin.params.BlufiParameter;
import vn.vconnex.blufi_plugin.response.BlufiScanResult;
import vn.vconnex.blufi_plugin.response.BlufiStatusResponse;
import vn.vconnex.blufi_plugin.response.BlufiVersionResponse;

/** BlufiPlugin */
public class BlufiPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler {

  private static final long TIMEOUT_SCAN = 4000L;
  private static final int REQUEST_FINE_LOCATION_PERMISSIONS = 1452;


  private List<ScanResult> mBleList;

  private Map<String, ScanResult> mDeviceMap;
  private ScanCallback mScanCallback;
  private String mBlufiFilter;
  private volatile long mScanStartTime;

  private ExecutorService mThreadPool;
  private Future mUpdateFuture;

  private BluetoothDevice mDevice;
  private BlufiClient mBlufiClient;
  private volatile boolean mConnected;

  private Context mContext;
  private ActivityPluginBinding activityBinding;

  private EventChannel stateChannel;
  private EventChannel.StreamHandler streamHandler;
  private EventChannel.EventSink sink;

  private final BlufiLog mLog = new BlufiLog(getClass());
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;

  private Handler handler;



  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    handler = new Handler(Looper.getMainLooper());
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "blufi_plugin");
    channel.setMethodCallHandler(this);
    stateChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), "blufi_plugin/state");
    streamHandler = new EventChannel.StreamHandler() {
      @Override
      public void onListen(Object arguments, EventChannel.EventSink events) {
        sink = events;
      }

      @Override
      public void onCancel(Object arguments) {
        sink = null;

      }
    };
    stateChannel.setStreamHandler(streamHandler);
    mContext = flutterPluginBinding.getApplicationContext();
    mThreadPool = Executors.newSingleThreadExecutor();
    mBleList = new LinkedList<>();
    mDeviceMap = new HashMap<>();
    mScanCallback = new ScanCallback();
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + Build.VERSION.RELEASE);
    }
    else if (call.method.equals("scanDeviceInfo")) {
      if (ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION)
              != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(
                activityBinding.getActivity(),
                new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION
                },
                REQUEST_FINE_LOCATION_PERMISSIONS);
      }
        String filter = call.argument("filter");
        scan(filter, result);
    }
    else if (call.method.equals("stopScan")) {
        stopScan();
    }
    else if (call.method.equals("connectPeripheral")) {
      String deviceId = call.argument("peripheral");
      if (deviceId != null) {
        connectDevice(mDeviceMap.get(deviceId).getDevice());
        result.success(true);
      }
      else {
        result.success(false);
      }
    }
    else if (call.method.equals("requestCloseConnection")) {
      disconnectGatt();
    }
    else if (call.method.equals("negotiateSecurity")) {
      negotiateSecurity();
    }
    else if (call.method.equals("requestDeviceVersion")) {
      requestDeviceVersion();
    }
    else if (call.method.equals("configProvision")) {
      String userName = call.argument("username");
      String password = call.argument("password");
      configure(userName, password);
    }
    else if (call.method.equals("requestDeviceStatus")) {
      requestDeviceStatus();
    }
    else if (call.method.equals("requestDeviceScan")) {
      requestDeviceWifiScan();
    }
    else if (call.method.equals("postCustomData")) {
      String dataStr = call.argument("custom_data");
      postCustomData(dataStr);
    }
    else {
      result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }


  private void scan(String filter, Result result) {
    startScan21(filter, result);
  }

  private void startScan21(String filter, Result result) {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
    if (!adapter.isEnabled() || scanner == null) {
      result.success(false);

      return;
    }

    mDeviceMap.clear();
    mBleList.clear();
    mBlufiFilter = filter;
    mScanStartTime = SystemClock.elapsedRealtime();

    mLog.d("Start scan ble");
    scanner.startScan(null, new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            mScanCallback);
    result.success(false);
  }

  private void stopScan() {
    stopScan21();
  }

  private void stopScan21() {
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
    if (scanner != null) {
      scanner.stopScan(mScanCallback);
    }
    if (mUpdateFuture != null) {
      mUpdateFuture.cancel(true);
    }
    mLog.d("Stop scan ble");
    updateMessage(makeJson("stop_scan_ble","1"));
  }

  void connectDevice(BluetoothDevice device) {
    mDevice = device;
    if (mBlufiClient != null) {
      mBlufiClient.close();
      mBlufiClient = null;
    }

    mBlufiClient = new BlufiClient(mContext, mDevice);
    mBlufiClient.setGattCallback(new GattCallback());
    mBlufiClient.setBlufiCallback(new BlufiCallbackMain());
    mBlufiClient.connect();
  }


  private void disconnectGatt() {
    if (mBlufiClient != null) {
      mBlufiClient.requestCloseConnection();
    }
  }

  /**
   * If negotiate security success, the continue communication data will be encrypted.
   */
  private void negotiateSecurity() {
    mBlufiClient.negotiateSecurity();
  }


  private void configure(String userName, String password) {
    BlufiConfigureParams params = new BlufiConfigureParams();
    params.setOpMode(1);
    byte[] ssidBytes = (byte[]) userName.getBytes();
    params.setStaSSIDBytes(ssidBytes);
    params.setStaPassword(password);
    mBlufiClient.configure(params);
  }

  /**
   * Request to get device current status
   */
  private void requestDeviceStatus() {
    mBlufiClient.requestDeviceStatus();
  }

  /**
   * Request to get device blufi version
   */
  private void requestDeviceVersion() {
    mBlufiClient.requestDeviceVersion();
  }

  /**
   * Request to get AP list that the device scanned
   */
  private void requestDeviceWifiScan() {
    mBlufiClient.requestDeviceWifiScan();
  }

  /**
   * Try to post custom data
   */
  private void postCustomData(String dataString) {
    if (dataString != null) {
      mBlufiClient.postCustomData(dataString.getBytes());
    }
  }

  private void onGattConnected() {
    mConnected = true;
  }

  private void onGattDisconnected() {
    mConnected = false;
  }

  private void onGattServiceCharacteristicDiscovered() {

  }


  @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
  private class GattCallback extends BluetoothGattCallback {
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
      String devAddr = gatt.getDevice().getAddress();
      mLog.d(String.format(Locale.ENGLISH, "onConnectionStateChange addr=%s, status=%d, newState=%d",
              devAddr, status, newState));
      if (status == BluetoothGatt.GATT_SUCCESS) {
        switch (newState) {
          case BluetoothProfile.STATE_CONNECTED:
            onGattConnected();
            updateMessage(makeJson("peripheral_connect","1"));
//            updateMessage(String.format("Connected %s", devAddr), false);
            break;
          case BluetoothProfile.STATE_DISCONNECTED:
            gatt.close();
            onGattDisconnected();
            updateMessage(makeJson("peripheral_connect","0"));
//            updateMessage(String.format("Disconnected %s", devAddr), false);
            break;
        }
      } else {
        gatt.close();
        onGattDisconnected();
        updateMessage(makeJson("peripheral_disconnect","1"));
//        updateMessage(String.format(Locale.ENGLISH, "Disconnect %s, status=%d", devAddr, status),
//                false);
      }
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
      mLog.d(String.format(Locale.ENGLISH, "onMtuChanged status=%d, mtu=%d", status, mtu));
      if (status == BluetoothGatt.GATT_SUCCESS) {
        mBlufiClient.setPostPackageLengthLimit(20);
//        updateMessage(makeJson("peripheral_disconnect","1"));
//        updateMessage(String.format(Locale.ENGLISH, "Set mtu complete, mtu=%d ", mtu), false);
      } else {
        mBlufiClient.setPostPackageLengthLimit(20);
//        updateMessage(String.format(Locale.ENGLISH, "Set mtu failed, mtu=%d, status=%d", mtu, status), false);
      }

      onGattServiceCharacteristicDiscovered();
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
      mLog.d(String.format(Locale.ENGLISH, "onServicesDiscovered status=%d", status));
      if (status != BluetoothGatt.GATT_SUCCESS) {
        gatt.disconnect();
        updateMessage(makeJson("discover_services","1"));
//        updateMessage(String.format(Locale.ENGLISH, "Discover services error status %d", status));
      }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
      mLog.d(String.format(Locale.ENGLISH, "onDescriptorWrite status=%d", status));
      if (descriptor.getUuid().equals(BlufiParameter.UUID_NOTIFICATION_DESCRIPTOR) &&
              descriptor.getCharacteristic().getUuid().equals(BlufiParameter.UUID_NOTIFICATION_CHARACTERISTIC)) {
        String msg = String.format(Locale.ENGLISH, "Set notification enable %s", (status == BluetoothGatt.GATT_SUCCESS ? " complete" : " failed"));
//        updateMessage(msg);
      }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
      if (status != BluetoothGatt.GATT_SUCCESS) {
        gatt.disconnect();
//        updateMessage(String.format(Locale.ENGLISH, "WriteChar error status %d", status));
      }
    }
  }

  private class BlufiCallbackMain extends BlufiCallback {
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onGattPrepared(BlufiClient client, BluetoothGatt gatt, BluetoothGattService service,
                               BluetoothGattCharacteristic writeChar, BluetoothGattCharacteristic notifyChar) {
      if (service == null) {
        mLog.w("Discover service failed");
        gatt.disconnect();
//        updateMessage("Discover service failed");
        updateMessage(makeJson("discover_service","0"));
        return;
      }
      if (writeChar == null) {
        mLog.w("Get write characteristic failed");
        gatt.disconnect();
//        updateMessage("Get write characteristic failed");
        updateMessage(makeJson("get_write_characteristic","0"));
        return;
      }
      if (notifyChar == null) {
        mLog.w("Get notification characteristic failed");
        gatt.disconnect();
//        updateMessage("Get notification characteristic failed");
        updateMessage(makeJson("get_notification_characteristic","0"));
        return;
      }
      updateMessage(makeJson("discover_service","1"));
//      updateMessage("Discover service and characteristics success");

      int mtu = BlufiConstants.DEFAULT_MTU_LENGTH;
      mLog.d("Request MTU " + mtu);
      boolean requestMtu = false;
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        requestMtu = gatt.requestMtu(mtu);
        updateMessage(makeJson("request_mtu","1"));
      }
      if (!requestMtu) {
        mLog.w("Request mtu failed");
        updateMessage(makeJson("request_mtu","0"));
//        updateMessage(String.format(Locale.ENGLISH, "Request mtu %d failed", mtu));
        onGattServiceCharacteristicDiscovered();
      }
    }

    @Override
    public void onNegotiateSecurityResult(BlufiClient client, int status) {
      if (status == STATUS_SUCCESS) {
//        updateMessage("Negotiate security complete");
        updateMessage(makeJson("negotiate_security","1"));
      } else {
//        updateMessage("Negotiate security failed， code=" + status);
        updateMessage(makeJson("negotiate_security","0"));
      }
    }

    @Override
    public void onPostConfigureParams(BlufiClient client, int status) {
      if (status == STATUS_SUCCESS) {
//        updateMessage("Post configure params complete");
        updateMessage(makeJson("configure_params","1"));
      } else {
        updateMessage(makeJson("configure_params","0"));
      }
    }

    @Override
    public void onDeviceStatusResponse(BlufiClient client, int status, BlufiStatusResponse response) {
      if (status == STATUS_SUCCESS) {
        updateMessage(makeJson("device_status","1"));
//        updateMessage(String.format("Receive device status response:\n%s"));
        if (response.isStaConnectWifi()){
          updateMessage(makeJson("device_wifi_connect","1"));
        } else {
          updateMessage(makeJson("device_wifi_connect","0"));
        }
      } else {
        updateMessage(makeJson("device_status","0"));
//        updateMessage("Device status response error, code=" + status);
      }
    }

    @Override
    public void onDeviceScanResult(BlufiClient client, int status, List<BlufiScanResult> results) {
      if (status == STATUS_SUCCESS) {
//        StringBuilder msg = new StringBuilder();
//        msg.append("Receive device scan result:\n");
        for (BlufiScanResult scanResult : results) {
//          msg.append(scanResult.toString()).append("\n");
          updateMessage(makeWifiInfoJson(scanResult.getSsid(),scanResult.getRssi()));
        }
//        updateMessage(msg.toString());
      } else {
        updateMessage(makeJson("wifi_info","0"));
//        updateMessage("Device scan result error, code=" + status);
      }
    }

    @Override
    public void onDeviceVersionResponse(BlufiClient client, int status, BlufiVersionResponse response) {
      if (status == STATUS_SUCCESS) {
        updateMessage(makeJson("device_version",response.getVersionString()));
//        updateMessage(String.format("Receive device version: %s", response.getVersionString()));
      } else {
        updateMessage(makeJson("device_version","0"));
//        updateMessage("Device version error, code=" + status);
      }

    }

    @Override
    public void onPostCustomDataResult(BlufiClient client, int status, byte[] data) {
      String dataStr = new String(data);
      String format = "Post data %s %s";
      if (status == STATUS_SUCCESS) {
        updateMessage(makeJson("post_custom_data","1"));
//        updateMessage(String.format(format, dataStr, "complete"));
      } else {
        updateMessage(makeJson("post_custom_data","0"));
//        updateMessage(String.format(format, dataStr, "failed"));
      }
    }

    @Override
    public void onReceiveCustomData(BlufiClient client, int status, byte[] data) {
      if (status == STATUS_SUCCESS) {
        String customStr = new String(data);
          customStr = customStr.replace("\"","\\\"");
//        updateMessage(String.format("Receive custom data:\n%s", customStr));
        updateMessage(makeJson("receive_device_custom_data",customStr));
      } else {
        updateMessage(makeJson("receive_device_custom_data","0"));
//        updateMessage("Receive custom data error, code=" + status);
      }
    }

    @Override
    public void onError(BlufiClient client, int errCode) {
      updateMessage(makeJson("receive_error_code",errCode + ""));
//      updateMessage(String.format(Locale.ENGLISH, "Receive error code %d", errCode));
    }
  }


  private void updateMessage(String message) {
    Log.v("message", message);

    if (sink != null) {
      handler.post(
              new Runnable() {
                @Override
                public void run() {
                  sink.success(message);
                }
              });
    }
  }

  private String makeJson(String command, String data) {

    String address = "";
    if (mDevice != null) {
      address = mDevice.getAddress();
    }
    return String.format("{\"key\":\"%s\",\"value\":\"%s\",\"address\":\"%s\"}", command, data, address);
  }

  private String makeScanDeviceJson(String address, String name, int rssi) {

    return String.format("{\"key\":\"ble_scan_result\",\"value\":{\"address\":\"%s\",\"name\":\"%s\",\"rssi\":\"%s\"}}", address, name,rssi);
  }

  private String makeWifiInfoJson(String ssid, int rssi) {
    String address = "";
    if (mDevice != null) {
      address = mDevice.getAddress();
    }
    return String.format("{\"key\":\"wifi_info\",\"value\":{\"ssid\":\"%s\",\"rssi\":\"%s\",\"address\":\"%s\"}}", ssid, rssi, address);
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private class ScanCallback extends android.bluetooth.le.ScanCallback {

    @Override
    public void onScanFailed(int errorCode) {
      super.onScanFailed(errorCode);
    }

    @Override
    public void onBatchScanResults(List<ScanResult> results) {
      for (ScanResult result : results) {
        onLeScan(result);
      }
    }

    @Override
    public void onScanResult(int callbackType, ScanResult result) {
      onLeScan(result);
    }

    private void onLeScan(ScanResult scanResult) {
      String name = scanResult.getDevice().getName();

      if (!TextUtils.isEmpty(mBlufiFilter)) {
        if (name == null || !name.toLowerCase().contains(mBlufiFilter.toLowerCase())) {
          return;
        }
      }

      Log.v("ble scan", scanResult.getDevice().getAddress());

      if (scanResult.getDevice().getName() != null) {
        mDeviceMap.put(scanResult.getDevice().getAddress(), scanResult);
        updateMessage(makeScanDeviceJson(scanResult.getDevice().getAddress(), scanResult.getDevice().getName(), scanResult.getRssi()));
      }
    }
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    activityBinding = binding;
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
  }

  @Override
  public void onDetachedFromActivity() {
  }
}
