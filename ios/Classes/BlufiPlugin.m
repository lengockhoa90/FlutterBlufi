#import "BlufiPlugin.h"
#import "BlufiClient.h"
#import "ESPPeripheral.h"
#import "ESPFBYBLEHelper.h"
#import "ESPDataConversion.h"
#import <CoreLocation/CoreLocation.h>
#import <SystemConfiguration/CaptiveNetwork.h>

@interface BlufiPlugin() <CBCentralManagerDelegate, CBPeripheralDelegate, BlufiDelegate>
@property(nonatomic, strong) ESPFBYBLEHelper *espFBYBleHelper;
@property(nonatomic, copy) NSMutableDictionary *peripheralDictionary;
//@property(nonatomic, copy)   NSMutableArray<ESPPeripheral *> *peripheralArray;
@property(nonatomic, strong) NSString *filterContent;
@property(strong, nonatomic)ESPPeripheral *device;
@property(strong, nonatomic)BlufiClient *blufiClient;
@property(assign, atomic)BOOL connected;
@property(nonatomic, retain) BlufiPluginStreamHandler *stateStreamHandler;
@end

@implementation BlufiPlugin


+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  FlutterMethodChannel* channel = [FlutterMethodChannel
      methodChannelWithName:@"blufi_plugin"
            binaryMessenger:[registrar messenger]];
    BlufiPlugin* instance = [[BlufiPlugin alloc] init];
     FlutterEventChannel* stateChannel = [FlutterEventChannel eventChannelWithName:@"blufi_plugin/state" binaryMessenger:[registrar messenger]];
    BlufiPluginStreamHandler* stateStreamHandler = [[BlufiPluginStreamHandler alloc] init];
     [stateChannel setStreamHandler:stateStreamHandler];
     instance.stateStreamHandler = stateStreamHandler;
    
  [registrar addMethodCallDelegate:instance channel:channel];
}


- (instancetype)init {
  self = [super init];
  if (self) {
    self.espFBYBleHelper = [ESPFBYBLEHelper share];
      self.filterContent = [ESPDataConversion loadBlufiScanFilter];
  }
  return self;
}

- (void)scanDeviceInfo {
    [self.espFBYBleHelper startScan:^(ESPPeripheral * _Nonnull device) {
        
        if (device.name == nil) return;

        if (self.filterContent != nil && ![self.filterContent isEqualToString:@""] && ![device.name.lowercaseString containsString:self.filterContent.lowercaseString]) return;
        
        self.dataDictionary[device.uuid.UUIDString] = device;
        [self updateMessage:[self makeScanDeviceJsonWithAddress:device.uuid.UUIDString name:device.name rssi:device.rssi]];
    }];
}

-(void)stopScan {
    [self.espFBYBleHelper stopScan];
    [self updateMessage:[self makeJsonWithCommand:@"stop_scan_ble" data:@"1"]];
}


- (void)connectPeripheral:(ESPPeripheral *)perripheral {
    self.connected = NO;
    self.device = perripheral;
    
    if (_blufiClient) {
        [_blufiClient close];
        _blufiClient = nil;
    }
    
    _blufiClient = [[BlufiClient alloc] init];
    _blufiClient.centralManagerDelete = self;
    _blufiClient.peripheralDelegate = self;
    _blufiClient.blufiDelegate = self;
    [_blufiClient connect:_device.uuid.UUIDString];
}

- (void)onDisconnected {
    if (_blufiClient) {
        [_blufiClient close];
    }
}

- (void)requestCloseConnection {
     if (_blufiClient) {
         [_blufiClient requestCloseConnection];
    }
}

-(void)negotiateSecurity {
    if (_blufiClient) {
        [_blufiClient negotiateSecurity];
    }
}

-(void) requestDeviceVersion {
    if (_blufiClient) {
        [_blufiClient requestDeviceVersion];
    }
}

-(void)configProvisionWithSSID: (NSString *)ssid password:(NSString *)password {
     BlufiConfigureParams *params = [[BlufiConfigureParams alloc] init];
    params.opMode = OpModeSta;
    params.staSsid = ssid;
    params.staPassword = password;
    
    if (_blufiClient && _connected) {
           [_blufiClient configure:params];
       }
}

-(void)requestDeviceStatus {
    if (_blufiClient) {
        [_blufiClient requestDeviceStatus];
    }
}

-(void)requestDeviceScan {
    if (_blufiClient) {
        [_blufiClient requestDeviceScan];
    }
}

-(void)postCustomData:(NSString *) data {
    
    if (_blufiClient && data != nil) {
        [_blufiClient postCustomData:[data dataUsingEncoding:NSUTF8StringEncoding]];
    }
}

- (NSMutableDictionary *) dataDictionary {
    if (!_peripheralDictionary) {
        _peripheralDictionary = [[NSMutableDictionary alloc] init];
    }
    return _peripheralDictionary;
}



- (void)centralManager:(CBCentralManager *)central didConnectPeripheral:(CBPeripheral *)peripheral {
    [self updateMessage:[self makeJsonWithCommand:@"peripheral_connect" data:@"1"]];
}

- (void)centralManager:(CBCentralManager *)central didFailToConnectPeripheral:(CBPeripheral *)peripheral error:(NSError *)error {
    [self updateMessage:[self makeJsonWithCommand:@"peripheral_connect" data:@"0"]];
    self.connected = NO;
}

- (void)centralManager:(CBCentralManager *)central didDisconnectPeripheral:(CBPeripheral *)peripheral error:(NSError *)error {
    [self onDisconnected];
    [self updateMessage:[self makeJsonWithCommand:@"peripheral_disconnect" data:@"1"]];
    self.connected = NO;
}

- (void)centralManagerDidUpdateState:(CBCentralManager *)central {
    
}

- (void)blufi:(BlufiClient *)client gattPrepared:(BlufiStatusCode)status service:(CBService *)service writeChar:(CBCharacteristic *)writeChar notifyChar:(CBCharacteristic *)notifyChar {
    if (status == StatusSuccess) {
        self.connected = YES;
        [self updateMessage:[self makeJsonWithCommand:@"blufi_connect_prepared" data:@"1"]];
    } else {
        [self onDisconnected];
        if (!service) {
            [self updateMessage:[self makeJsonWithCommand:@"blufi_connect_prepared" data:@"2"]];
        } else if (!writeChar) {
            [self updateMessage:[self makeJsonWithCommand:@"blufi_connect_prepared" data:@"3"]];
        } else if (!notifyChar) {
            [self updateMessage:[self makeJsonWithCommand:@"blufi_connect_prepared" data:@"4"]];
        }
    }
}

- (void)blufi:(BlufiClient *)client didNegotiateSecurity:(BlufiStatusCode)status {
    NSLog(@"Blufi didNegotiateSecurity %d", status);
   
    if (status == StatusSuccess) {
        [self updateMessage:[self makeJsonWithCommand:@"negotiate_security" data:@"1"]];
    } else {
        [self updateMessage:[self makeJsonWithCommand:@"negotiate_security" data:@"0"]];
    }
}

- (void)blufi:(BlufiClient *)client didReceiveDeviceVersionResponse:(BlufiVersionResponse *)response status:(BlufiStatusCode)status {
    
    if (status == StatusSuccess) {
        [self updateMessage:[self makeJsonWithCommand:@"device_version" data:response.getVersionString]];
    } else {
        [self updateMessage:[self makeJsonWithCommand:@"device_version" data:@"0"]];
    }
}

- (void)blufi:(BlufiClient *)client didPostConfigureParams:(BlufiStatusCode)status {
    if (status == StatusSuccess) {
        [self updateMessage:[self makeJsonWithCommand:@"configure_params" data:@"1"]];
    } else {
        [self updateMessage:[self makeJsonWithCommand:@"configure_params" data:@"0"]];
    }
}

- (void)blufi:(BlufiClient *)client didReceiveDeviceStatusResponse:(BlufiStatusResponse *)response status:(BlufiStatusCode)status {
   
    if (status == StatusSuccess) {
        [self updateMessage:[self makeJsonWithCommand:@"device_status" data:@"1"]];
        
        if ([response isStaConnectWiFi]) {
          [self updateMessage:[self makeJsonWithCommand:@"device_wifi_connect" data:@"1"]];
        } else {
                [self updateMessage:[self makeJsonWithCommand:@"device_wifi_connect" data:@"0"]];
      }
    } else {
        [self updateMessage:[self makeJsonWithCommand:@"device_status" data:@"0"]];
    }
}

- (void)blufi:(BlufiClient *)client didReceiveDeviceScanResponse:(NSArray<BlufiScanResponse *> *)scanResults status:(BlufiStatusCode)status {
  
    if (status == StatusSuccess) {
//        NSMutableString *info = [[NSMutableString alloc] init];
//        [info appendString:@"Receive device scan results:\n"];
        for (BlufiScanResponse *response in scanResults) {
//            [info appendFormat:@"SSID: %@, RSSI: %d\n", response.ssid, response.rssi];
            [self updateMessage:[self makeWifiInfoJsonWithSsid:response.ssid rssi:response.rssi]];
        }
    } else {
        [self updateMessage:[self makeJsonWithCommand:@"wifi_info" data:@"0"]];
    }
}

- (void)blufi:(BlufiClient *)client didPostCustomData:(nonnull NSData *)data status:(BlufiStatusCode)status {
    if (status == StatusSuccess) {
        [self updateMessage:[self makeJsonWithCommand:@"post_custom_data" data:@"1"]];
    } else {
        [self updateMessage:[self makeJsonWithCommand:@"post_custom_data" data:@"0"]];
    }
}

- (void)blufi:(BlufiClient *)client didReceiveCustomData:(NSData *)data status:(BlufiStatusCode)status {
    if (status == StatusSuccess) {
        NSString *customString = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
        customString = [customString stringByReplacingOccurrencesOfString:@"\"" withString:@"\\\""];
           [self updateMessage:[self makeJsonWithCommand:@"receive_device_custom_data" data:customString]];
    }
    else {
        [self updateMessage:[self makeJsonWithCommand:@"receive_device_custom_data" data:@"0"]];
    }
   
}

- (void)updateMessage:(NSString *)message {
    NSLog(@"%@", message);
    
    if(_stateStreamHandler.sink != nil) {
      self.stateStreamHandler.sink(message);
    }
}

-(NSString *)makeJsonWithCommand:(NSString*)command data:(NSString *)data {
    NSString *address = @"";
    if (self.device != nil) {
        address = self.device.uuid.UUIDString;
    }
    
    return [NSString stringWithFormat:@"{\"key\":\"%@\",\"value\":\"%@\",\"address\":\"%@\"}",command, data, address];
}

-(NSString *)makeScanDeviceJsonWithAddress:(NSString*)address name:(NSString *)name rssi: (int)rssi {
    return [NSString stringWithFormat:@"{\"key\":\"ble_scan_result\",\"value\":{\"address\":\"%@\",\"name\":\"%@\",\"rssi\":\"%d\"}}",address, name,rssi];
}

-(NSString *)makeWifiInfoJsonWithSsid:(NSString*)ssid rssi:(int)rssi {
    NSString *address = @"";
       if (self.device != nil) {
           address = self.device.uuid.UUIDString;
       }
    return [NSString stringWithFormat:@"{\"key\":\"wifi_info\",\"value\":{\"ssid\":\"%@\",\"rssi\":\"%d\",\"address\":\"%@\"}}",ssid, rssi,address];
}


- (void)handleMethodCall:(FlutterMethodCall*)call result:(FlutterResult)result {
  if ([@"getPlatformVersion" isEqualToString:call.method]) {
    result([@"iOS " stringByAppendingString:[[UIDevice currentDevice] systemVersion]]);
    } else if ([@"scanDeviceInfo" isEqualToString:call.method]) {
        
        NSString *filter = call.arguments[@"filter"];
        if (filter != nil) {
             self.filterContent = filter;
        }
        [self scanDeviceInfo];
        
    }
    else if ([@"stopScan" isEqualToString:call.method]) {
        [self stopScan];
    }
    else if ([@"connectPeripheral" isEqualToString:call.method]) {
        NSString *peripheral = call.arguments[@"peripheral"];
        [self connectPeripheral:self.peripheralDictionary[peripheral]];
    }
    else if ([@"requestCloseConnection" isEqualToString:call.method]) {
        [self requestCloseConnection];
    }
    else if ([@"negotiateSecurity" isEqualToString:call.method]) {
        [self negotiateSecurity];
    }
    else if ([@"requestDeviceVersion" isEqualToString:call.method]) {
        [self requestDeviceVersion];
    }
    else if ([@"configProvision" isEqualToString:call.method]) {
            NSString *username = call.arguments[@"username"];
            NSString *password = call.arguments[@"password"];
          [self configProvisionWithSSID:username password:password];
    }
    else if ([@"requestDeviceStatus" isEqualToString:call.method]) {
        [self requestDeviceStatus];
    }
    else if ([@"requestDeviceScan" isEqualToString:call.method]) {
        [self requestDeviceScan];
    }
    else if ([@"postCustomData" isEqualToString:call.method]) {
        NSString *customData = call.arguments[@"custom_data"];
        [self postCustomData:customData];
    }
  else {
    result(FlutterMethodNotImplemented);
  }
}

@end

@implementation BlufiPluginStreamHandler

- (FlutterError*)onListenWithArguments:(id)arguments eventSink:(FlutterEventSink)eventSink {
  self.sink = eventSink;
  return nil;
}

- (FlutterError*)onCancelWithArguments:(id)arguments {
  self.sink = nil;
  return nil;
}

@end
