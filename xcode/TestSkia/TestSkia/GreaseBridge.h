#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

extern NSString *const GreaseBridgeScriptMessageName;

typedef NSString *_Nonnull (^GreaseBridgeFunctionHandler)(NSString *payloadJSON);

@interface GreaseBridge : NSObject

- (instancetype)initWithFunctionTree:(NSDictionary *)functionTree
                             handler:(nullable GreaseBridgeFunctionHandler)handler;
- (NSString *)bootstrapJavaScript;
- (NSDictionary *)responseForScriptMessageBody:(id)body;

@end

NS_ASSUME_NONNULL_END
