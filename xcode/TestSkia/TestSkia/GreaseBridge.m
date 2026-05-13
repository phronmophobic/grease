#import "GreaseBridge.h"

#import <UIKit/UIKit.h>
#import <sys/sysctl.h>

NSString *const GreaseBridgeScriptMessageName = @"greaseBridge";

static id GreaseJSONObjectOrNull(id value) { return value ?: [NSNull null]; }

static NSString *GreaseHardwareModel(void) {
  size_t size = 0;
  sysctlbyname("hw.machine", NULL, &size, NULL, 0);
  if (size == 0) {
    return UIDevice.currentDevice.model ?: @"unknown";
  }

  char *machine = malloc(size);
  if (machine == NULL) {
    return UIDevice.currentDevice.model ?: @"unknown";
  }

  sysctlbyname("hw.machine", machine, &size, NULL, 0);
  NSString *model = [NSString stringWithUTF8String:machine] ?: @"unknown";
  free(machine);
  return model;
}

@implementation GreaseBridge

+ (NSString *)bootstrapJavaScript {
  return @"(function(){"
         @"if(window.Grease&&window.Grease.__nativeBridgeVersion){return;}"
         @"var callbacks={};"
         @"var nextId=String(Date.now())+'-0';"
         @"function newId(){var parts=nextId.split('-');var "
         @"n=Number(parts[1])+1;nextId=parts[0]+'-'+n;return nextId;}"
         @"function nativeCallback(response){"
         @"  if(!response){return;}"
         @"  var callback=callbacks[response.id];"
         @"  if(!callback){return;}"
         @"  if(!response.keepCallback){delete callbacks[response.id];}"
         @"  if(response.ok){callback.resolve(response.value);}"
         @"  else{var message=(response.value&&response.value.message)||response.status||'Native "
         @"bridge error';"
         @"       var error=new "
         @"Error(message);error.status=response.status;error.value=response.value;callback.reject("
         @"error);}"
         @"}"
         @"function exec(service,action,args){"
         @"  return new Promise(function(resolve,reject){"
         @"    "
         @"if(!window.webkit||!window.webkit.messageHandlers||!window.webkit.messageHandlers."
         @"greaseBridge){"
         @"      reject(new Error('Grease native bridge is unavailable'));return;"
         @"    }"
         @"    var id=newId();"
         @"    callbacks[id]={resolve:resolve,reject:reject};"
         @"    "
         @"window.webkit.messageHandlers.greaseBridge.postMessage({id:id,service:service,action:"
         @"action,args:args||[]});"
         @"  });"
         @"}"
         @"var grease=window.Grease||{};"
         @"grease.__nativeBridgeVersion=1;"
         @"grease.exec=exec;"
         @"grease._nativeCallback=nativeCallback;"
         @"grease.device={getInfo:function(){return exec('Device','getInfo',[]);}};"
         @"window.Grease=grease;"
         @"})();";
}

- (NSDictionary *)responseForScriptMessageBody:(id)body {
  if (![body isKindOfClass:NSDictionary.class]) {
    return [self responseWithId:nil
                             ok:NO
                         status:@"bad_request"
                          value:@{@"message" : @"Bridge message must be an object"}
                   keepCallback:NO];
  }

  NSDictionary *message = (NSDictionary *)body;
  id callbackId = message[@"id"];
  NSString *service =
      [message[@"service"] isKindOfClass:NSString.class] ? message[@"service"] : nil;
  NSString *action = [message[@"action"] isKindOfClass:NSString.class] ? message[@"action"] : nil;
  id args = message[@"args"];

  if (![callbackId isKindOfClass:NSString.class] && ![callbackId isKindOfClass:NSNumber.class]) {
    return [self responseWithId:nil
                             ok:NO
                         status:@"bad_request"
                          value:@{@"message" : @"Bridge message id must be a string or number"}
                   keepCallback:NO];
  }

  if (service.length == 0 || action.length == 0) {
    return [self responseWithId:callbackId
                             ok:NO
                         status:@"bad_request"
                          value:@{@"message" : @"Bridge message requires service and action"}
                   keepCallback:NO];
  }

  if (args != nil && ![args isKindOfClass:NSArray.class]) {
    return [self responseWithId:callbackId
                             ok:NO
                         status:@"bad_request"
                          value:@{@"message" : @"Bridge args must be an array"}
                   keepCallback:NO];
  }

  if (![service isEqualToString:@"Device"]) {
    return [self responseWithId:callbackId
                             ok:NO
                         status:@"service_not_found"
                          value:@{@"message" : @"Unknown bridge service"}
                   keepCallback:NO];
  }

  if (![action isEqualToString:@"getInfo"]) {
    return [self responseWithId:callbackId
                             ok:NO
                         status:@"action_not_found"
                          value:@{@"message" : @"Unknown Device action"}
                   keepCallback:NO];
  }

  return [self responseWithId:callbackId
                           ok:YES
                       status:@"ok"
                        value:[self deviceInfo]
                 keepCallback:NO];
}

- (NSDictionary *)deviceInfo {
  UIDevice *device = UIDevice.currentDevice;
  NSBundle *bundle = NSBundle.mainBundle;
  NSDictionary *info = bundle.infoDictionary ?: @{};
  NSUUID *identifier = device.identifierForVendor;

  return @{
    @"platform" : @"iOS",
    @"model" : GreaseJSONObjectOrNull(GreaseHardwareModel()),
    @"systemName" : GreaseJSONObjectOrNull(device.systemName),
    @"systemVersion" : GreaseJSONObjectOrNull(device.systemVersion),
    @"identifierForVendor" : GreaseJSONObjectOrNull(identifier.UUIDString),
    @"appName" : GreaseJSONObjectOrNull(info[@"CFBundleName"]),
    @"appVersion" : GreaseJSONObjectOrNull(info[@"CFBundleShortVersionString"]),
    @"bundleIdentifier" : GreaseJSONObjectOrNull(bundle.bundleIdentifier)
  };
}

- (NSDictionary *)responseWithId:(id)callbackId
                              ok:(BOOL)ok
                          status:(NSString *)status
                           value:(id)value
                    keepCallback:(BOOL)keepCallback {
  return @{
    @"id" : GreaseJSONObjectOrNull(callbackId),
    @"ok" : @(ok),
    @"status" : status,
    @"value" : GreaseJSONObjectOrNull(value),
    @"keepCallback" : @(keepCallback)
  };
}

@end
