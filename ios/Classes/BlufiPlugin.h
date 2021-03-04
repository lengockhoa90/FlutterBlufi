#if TARGET_OS_OSX
 #import <FlutterMacOS/FlutterMacOS.h>
#else
 #import <Flutter/Flutter.h>
#endif
#import <CoreBluetooth/CoreBluetooth.h>

@interface BlufiPlugin : NSObject<FlutterPlugin>
@end

@interface BlufiPluginStreamHandler : NSObject<FlutterStreamHandler>
@property FlutterEventSink sink;
@end
