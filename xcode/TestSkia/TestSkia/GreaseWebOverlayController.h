#import <UIKit/UIKit.h>

#import "GreaseBridge.h"

NS_ASSUME_NONNULL_BEGIN

@class WKWebView;

@interface GreaseWebOverlayController : UIViewController

@property (nonatomic, assign, readonly, getter=isClosed) BOOL closed;
@property (nonatomic, strong, readonly) WKWebView *webView;
@property (nonatomic, assign) BOOL webViewUsesSafeAreaLayoutGuide;

- (instancetype)initWithURLString:(NSString *)urlString;
- (instancetype)initWithURLString:(NSString *)urlString
                     functionTree:(NSDictionary *)functionTree
                          handler:(nullable GreaseBridgeFunctionHandler)handler;
- (BOOL)presentInViewController:(UIViewController *)parentViewController;
- (BOOL)loadURLString:(NSString *)urlString;
- (BOOL)evaluateJavaScriptString:(NSString *)javaScriptString;
- (BOOL)close;
- (void)reload;
- (void)goBack;

@end

NS_ASSUME_NONNULL_END
