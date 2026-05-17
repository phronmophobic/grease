#import "GreaseWebOverlayController.h"

#import <WebKit/WebKit.h>

@interface GreaseWebOverlayController () <WKScriptMessageHandler>

@property (nonatomic, strong) GreaseBridge *bridge;
@property (nonatomic, assign, readwrite, getter=isClosed) BOOL closed;
@property (nonatomic, strong, readwrite) WKWebView *webView;
@property (nonatomic, copy) NSString *initialURLString;
@property (nonatomic, copy) NSDictionary *functionTree;
@property (nonatomic, copy, nullable) GreaseBridgeFunctionHandler functionHandler;

@end

@implementation GreaseWebOverlayController

- (instancetype)initWithURLString:(NSString *)urlString {
  return [self initWithURLString:urlString functionTree:@{} handler:nil];
}

- (instancetype)initWithURLString:(NSString *)urlString
                     functionTree:(NSDictionary *)functionTree
                          handler:(nullable GreaseBridgeFunctionHandler)handler {
  self = [super initWithNibName:nil bundle:nil];
  if (self) {
    _initialURLString = [urlString copy];
    _functionTree = [functionTree copy] ?: @{};
    _functionHandler = [handler copy];
  }
  return self;
}

- (void)viewDidLoad {
  [super viewDidLoad];

  self.bridge = [[GreaseBridge alloc] initWithFunctionTree:self.functionTree
                                                   handler:self.functionHandler];
  self.view.backgroundColor = UIColor.systemBackgroundColor;

  WKUserContentController *contentController = [[WKUserContentController alloc] init];
  [contentController addScriptMessageHandler:self name:GreaseBridgeScriptMessageName];
  WKUserScript *bridgeScript =
      [[WKUserScript alloc] initWithSource:[self.bridge bootstrapJavaScript]
                             injectionTime:WKUserScriptInjectionTimeAtDocumentStart
                          forMainFrameOnly:NO];
  [contentController addUserScript:bridgeScript];

  WKWebViewConfiguration *configuration = [[WKWebViewConfiguration alloc] init];
  configuration.userContentController = contentController;

  self.webView = [[WKWebView alloc] initWithFrame:CGRectZero configuration:configuration];
  self.webView.translatesAutoresizingMaskIntoConstraints = NO;

  [self.view addSubview:self.webView];
  [NSLayoutConstraint activateConstraints:@[
    [self.webView.topAnchor constraintEqualToAnchor:self.view.topAnchor],
    [self.webView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
    [self.webView.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
    [self.webView.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor]
  ]];

  if (self.initialURLString.length > 0) {
    [self loadURLString:self.initialURLString];
  }
}

- (void)dealloc {
  [self.webView.configuration.userContentController
      removeScriptMessageHandlerForName:GreaseBridgeScriptMessageName];
}

- (BOOL)presentInViewController:(UIViewController *)parentViewController {
  if (self.closed) {
    return NO;
  }

  if (!parentViewController) {
    return NO;
  }

  if (self.parentViewController != nil) {
    [self.parentViewController.view bringSubviewToFront:self.view];
    return YES;
  }

  [parentViewController addChildViewController:self];
  self.view.translatesAutoresizingMaskIntoConstraints = NO;
  [parentViewController.view addSubview:self.view];

  [NSLayoutConstraint activateConstraints:@[
    [self.view.topAnchor constraintEqualToAnchor:parentViewController.view.topAnchor],
    [self.view.leadingAnchor constraintEqualToAnchor:parentViewController.view.leadingAnchor],
    [self.view.trailingAnchor constraintEqualToAnchor:parentViewController.view.trailingAnchor],
    [self.view.bottomAnchor constraintEqualToAnchor:parentViewController.view.bottomAnchor]
  ]];

  [self didMoveToParentViewController:parentViewController];
  return YES;
}

- (BOOL)loadURLString:(NSString *)urlString {
  if (self.closed) {
    return NO;
  }

  NSURL *url = [self.class normalizedURLFromString:urlString];
  if (!url) {
    return NO;
  }

  self.initialURLString = url.absoluteString;

  if (self.webView) {
    [self.webView loadRequest:[NSURLRequest requestWithURL:url]];
  }

  return YES;
}

- (BOOL)evaluateJavaScriptString:(NSString *)javaScriptString {
  if (self.closed || !self.webView || javaScriptString.length == 0) {
    return NO;
  }

  [self.webView evaluateJavaScript:javaScriptString completionHandler:nil];
  return YES;
}

- (BOOL)close {
  if (self.closed) {
    return NO;
  }

  self.closed = YES;
  [self willMoveToParentViewController:nil];
  [self.view removeFromSuperview];
  [self removeFromParentViewController];
  return YES;
}

- (void)reload {
  if (self.closed) {
    return;
  }

  if (self.webView.URL) {
    [self.webView reload];
  }
}

- (void)goBack {
  if (self.closed) {
    return;
  }

  if (self.webView.canGoBack) {
    [self.webView goBack];
  }
}

+ (NSURL *)normalizedURLFromString:(NSString *)text {
  NSString *trimmed =
      [text stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
  if (trimmed.length == 0) {
    return nil;
  }

  NSString *urlString = trimmed;
  if ([trimmed rangeOfString:@"://"].location == NSNotFound) {
    urlString = [@"http://" stringByAppendingString:trimmed];
  }

  NSURLComponents *components = [NSURLComponents componentsWithString:urlString];

  if (components.scheme.length == 0 || components.host.length == 0) {
    return nil;
  }

  return components.URL;
}

#pragma mark - WKScriptMessageHandler

- (void)userContentController:(WKUserContentController *)userContentController
      didReceiveScriptMessage:(WKScriptMessage *)message {
  if (![message.name isEqualToString:GreaseBridgeScriptMessageName]) {
    return;
  }

  NSDictionary *response = [self.bridge responseForScriptMessageBody:message.body];
  [self sendBridgeResponse:response];
}

- (void)sendBridgeResponse:(NSDictionary *)response {
  if (![NSJSONSerialization isValidJSONObject:response]) {
    NSLog(@"Invalid bridge response: %@", response);
    return;
  }

  NSError *error = nil;
  NSData *jsonData = [NSJSONSerialization dataWithJSONObject:response options:0 error:&error];
  if (!jsonData) {
    NSLog(@"Failed to encode bridge response: %@", error);
    return;
  }

  NSString *json = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
  NSString *script =
      [NSString stringWithFormat:@"window.Grease&&window.Grease._nativeCallback(%@);", json];
  [self.webView evaluateJavaScript:script completionHandler:nil];
}

@end
