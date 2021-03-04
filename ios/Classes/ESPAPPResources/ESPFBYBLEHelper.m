//
//  ESPFBYBLEHelper.m
//  EspBlufi
//
//  Created by fanbaoying on 2020/6/11.
//  Copyright © 2020 espressif. All rights reserved.
//

#import "ESPFBYBLEHelper.h"
#import <CoreBluetooth/CoreBluetooth.h>

API_AVAILABLE(ios(10.0))
@interface ESPFBYBLEHelper ()<CBCentralManagerDelegate,CBPeripheralDelegate>

@property (nonatomic, strong) CBCentralManager *centralManager;

@property (nonatomic, strong) NSMutableArray *peripherals;

@property (nonatomic, assign) CBManagerState peripheralState;

@end

@implementation ESPFBYBLEHelper

- (void)ESPFBYBLEHelperInit {
    self.centralManager = [[CBCentralManager alloc]initWithDelegate:self queue:nil];
}

//单例模式
+ (instancetype)share {
    static ESPFBYBLEHelper *share = nil;
    static dispatch_once_t oneToken;
    dispatch_once(&oneToken, ^{
        share = [[ESPFBYBLEHelper alloc]init];
        [share ESPFBYBLEHelperInit];
    });
    return share;
}

- (void)stopScan {
    [self.centralManager stopScan];
}

- (void)startScan:(FBYBleDeviceBackBlock)device {
    
    _bleScanSuccessBlock = device;
    if (@available(iOS 10.0, *)) {
        if (self.peripheralState ==  CBManagerStatePoweredOn)
        {
            [self.centralManager scanForPeripheralsWithServices:nil options:nil];
        }
    } else {
       if (self.peripheralState ==  CBCentralManagerStatePoweredOn)
        {
            [self.centralManager scanForPeripheralsWithServices:nil options:nil];
        }
    }
}

/**
 扫描到设备
 
 @param central 中心管理者
 @param peripheral 扫描到的设备
 @param advertisementData 广告信息
 @param RSSI 信号强度
 */
- (void)centralManager:(CBCentralManager *)central didDiscoverPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary<NSString *,id> *)advertisementData RSSI:(NSNumber *)RSSI {
    ESPPeripheral *espPeripheral = [[ESPPeripheral alloc] initWithPeripheral:peripheral];
    espPeripheral.name = [advertisementData objectForKey:@"kCBAdvDataLocalName"];
    espPeripheral.rssi = RSSI.intValue;
    if (self.bleScanSuccessBlock) {
        self.bleScanSuccessBlock(espPeripheral);
    }
}

// 状态更新时调用
- (void)centralManagerDidUpdateState:(CBCentralManager *)central
{
    switch (central.state) {
        case CBManagerStateUnknown:{
            self.peripheralState = central.state;
        }
            break;
        case CBManagerStateResetting:
        {
            self.peripheralState = central.state;
        }
            break;
        case CBManagerStateUnsupported:
        {
            self.peripheralState = central.state;
        }
            break;
        case CBManagerStateUnauthorized:
        {
            self.peripheralState = central.state;
        }
            break;
        case CBManagerStatePoweredOff:
        {
            self.peripheralState = central.state;
        }
            break;
        case CBManagerStatePoweredOn:
        {
            self.peripheralState = central.state;
            NSLog(@"%ld",(long)self.peripheralState);
            [self.centralManager scanForPeripheralsWithServices:nil options:nil];
        }
            break;
        default:
            break;
    }
}

@end
