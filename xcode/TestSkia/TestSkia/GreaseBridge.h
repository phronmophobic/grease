#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

extern NSString * const GreaseBridgeScriptMessageName;

@interface GreaseBridge : NSObject

+ (NSString *)bootstrapJavaScript;
- (NSDictionary *)responseForScriptMessageBody:(id)body;

@end

NS_ASSUME_NONNULL_END
