#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@class WKWebView;

@interface GreaseWebOverlayController : UIViewController

@property (nonatomic, strong, readonly) WKWebView *webView;

- (instancetype)initWithURLString:(NSString *)urlString;
- (BOOL)presentInViewController:(UIViewController *)parentViewController;
- (BOOL)loadURLString:(NSString *)urlString;
- (void)close;
- (void)reload;
- (void)goBack;

@end

NS_ASSUME_NONNULL_END
