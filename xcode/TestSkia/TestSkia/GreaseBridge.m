#import "GreaseBridge.h"

NSString *const GreaseBridgeScriptMessageName = @"greaseBridge";

static id GreaseJSONObjectOrNull(id value) { return value ?: [NSNull null]; }

@interface GreaseBridge ()

@property (nonatomic, copy) NSDictionary *functionTree;
@property (nonatomic, copy, nullable) GreaseBridgeFunctionHandler handler;

@end

@implementation GreaseBridge

- (instancetype)initWithFunctionTree:(NSDictionary *)functionTree
                             handler:(nullable GreaseBridgeFunctionHandler)handler {
  self = [super init];
  if (self) {
    _functionTree = [functionTree copy] ?: @{};
    _handler = [handler copy];
  }
  return self;
}

- (NSString *)bootstrapJavaScript {
  NSData *functionTreeData = [NSJSONSerialization dataWithJSONObject:self.functionTree
                                                             options:0
                                                               error:nil];
  NSString *functionTreeJSON =
      [[NSString alloc] initWithData:functionTreeData encoding:NSUTF8StringEncoding] ?: @"{}";

  return [NSString
      stringWithFormat:
          @"(function(){"
           "var functionTree=%@;"
           "var callbacks={};"
           "var nextId=String(Date.now())+'-0';"
           "function newId(){var parts=nextId.split('-');var n=Number(parts[1])+1;"
           "nextId=parts[0]+'-'+n;return nextId;}"
           "function nativeCallback(response){"
           "  if(!response){return;}"
           "  var callback=callbacks[response.id];"
           "  if(!callback){return;}"
           "  delete callbacks[response.id];"
           "  if(response.ok){callback.resolve(response.value);}"
           "  else{var message=(response.value&&response.value.message)||response.status||"
           "'Grease bridge error';"
           "       var error=new Error(message);error.status=response.status;"
           "       error.value=response.value;callback.reject(error);}"
           "}"
           "function exec(path,args){"
           "  return new Promise(function(resolve,reject){"
           "    if(!window.webkit||!window.webkit.messageHandlers||"
           "!window.webkit.messageHandlers.greaseBridge){"
           "      reject(new Error('Grease native bridge is unavailable'));return;"
           "    }"
           "    var id=newId();"
           "    callbacks[id]={resolve:resolve,reject:reject};"
           "    window.webkit.messageHandlers.greaseBridge.postMessage({"
           "id:id,path:path,args:Array.prototype.slice.call(args||[])});"
           "  });"
           "}"
           "function install(target,tree,path){"
           "  Object.keys(tree||{}).forEach(function(key){"
           "    var value=tree[key];"
           "    if(value&&typeof value==='object'&&!Array.isArray(value)){"
           "      var "
           "child=target[key]||{};target[key]=child;install(child,value,path.concat(key));"
           "    }else{"
           "      target[key]=function(){return exec(path.concat(key),arguments);};"
           "    }"
           "  });"
           "}"
           "window.Grease={__nativeBridgeVersion:2,_nativeCallback:nativeCallback};"
           "install(window.Grease,functionTree,[]);"
           "})();",
          functionTreeJSON];
}

- (NSDictionary *)responseForScriptMessageBody:(id)body {
  if (![body isKindOfClass:NSDictionary.class]) {
    return [self responseWithId:nil
                             ok:NO
                         status:@"bad_request"
                          value:@{@"message" : @"Bridge message must be an object"}];
  }

  NSDictionary *message = (NSDictionary *)body;
  id callbackId = message[@"id"];
  NSArray *path = [message[@"path"] isKindOfClass:NSArray.class] ? message[@"path"] : nil;
  id argsValue = message[@"args"];

  if (![callbackId isKindOfClass:NSString.class] && ![callbackId isKindOfClass:NSNumber.class]) {
    return [self responseWithId:nil
                             ok:NO
                         status:@"bad_request"
                          value:@{@"message" : @"Bridge message id must be a string or number"}];
  }

  if (path.count == 0) {
    return [self responseWithId:callbackId
                             ok:NO
                         status:@"bad_request"
                          value:@{@"message" : @"Bridge message requires a non-empty path"}];
  }

  for (id component in path) {
    if (![component isKindOfClass:NSString.class]) {
      return [self responseWithId:callbackId
                               ok:NO
                           status:@"bad_request"
                            value:@{@"message" : @"Bridge path components must be strings"}];
    }
  }

  if (argsValue && ![argsValue isKindOfClass:NSArray.class]) {
    return [self responseWithId:callbackId
                             ok:NO
                         status:@"bad_request"
                          value:@{@"message" : @"Bridge args must be an array"}];
  }

  if (!self.handler) {
    return [self responseWithId:callbackId
                             ok:NO
                         status:@"handler_not_found"
                          value:@{@"message" : @"No bridge handler is installed"}];
  }

  NSArray *args = argsValue ?: @[];
  NSDictionary *payload = @{@"path" : path, @"args" : args};
  NSData *payloadData = [NSJSONSerialization dataWithJSONObject:payload options:0 error:nil];
  NSString *payloadJSON = [[NSString alloc] initWithData:payloadData encoding:NSUTF8StringEncoding];
  NSString *resultJSON = self.handler(payloadJSON ?: @"{}");
  NSDictionary *result = [self resultFromJSON:resultJSON];

  BOOL ok = [result[@"ok"] boolValue];
  NSString *status = [result[@"status"] isKindOfClass:NSString.class] ? result[@"status"] : @"ok";
  id value = result[@"value"];

  return [self responseWithId:callbackId ok:ok status:status value:value];
}

- (NSDictionary *)resultFromJSON:(NSString *)json {
  if (![json isKindOfClass:NSString.class]) {
    return @{
      @"ok" : @NO,
      @"status" : @"bad_response",
      @"value" : @{@"message" : @"Handler did not return JSON"}
    };
  }

  NSData *data = [json dataUsingEncoding:NSUTF8StringEncoding];
  NSError *error = nil;
  id result = [NSJSONSerialization JSONObjectWithData:data options:0 error:&error];
  if (![result isKindOfClass:NSDictionary.class]) {
    return @{
      @"ok" : @NO,
      @"status" : @"bad_response",
      @"value" : @{@"message" : @"Handler returned invalid JSON"}
    };
  }

  return (NSDictionary *)result;
}

- (NSDictionary *)responseWithId:(id)callbackId
                              ok:(BOOL)ok
                          status:(NSString *)status
                           value:(id)value {
  return @{
    @"id" : GreaseJSONObjectOrNull(callbackId),
    @"ok" : @(ok),
    @"status" : status ?: @"ok",
    @"value" : GreaseJSONObjectOrNull(value),
    @"keepCallback" : @NO
  };
}

@end
