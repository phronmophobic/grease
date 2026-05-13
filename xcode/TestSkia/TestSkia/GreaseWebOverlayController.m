#import "GreaseWebOverlayController.h"

#import "GreaseBridge.h"

#import <WebKit/WebKit.h>

@interface GreaseWebOverlayController () <WKScriptMessageHandler, WKNavigationDelegate>

@property (nonatomic, strong) GreaseBridge *bridge;
@property (nonatomic, strong) WKWebView *webView;
@property (nonatomic, strong) UILabel *statusLabel;

@end

@implementation GreaseWebOverlayController

- (void)viewDidLoad
{
    [super viewDidLoad];

    self.bridge = [[GreaseBridge alloc] init];
    self.view.backgroundColor = UIColor.systemBackgroundColor;

    WKUserContentController *contentController = [[WKUserContentController alloc] init];
    [contentController addScriptMessageHandler:self name:GreaseBridgeScriptMessageName];
    WKUserScript *bridgeScript = [[WKUserScript alloc] initWithSource:[GreaseBridge bootstrapJavaScript]
                                                        injectionTime:WKUserScriptInjectionTimeAtDocumentStart
                                                     forMainFrameOnly:NO];
    [contentController addUserScript:bridgeScript];

    WKWebViewConfiguration *configuration = [[WKWebViewConfiguration alloc] init];
    configuration.userContentController = contentController;

    self.webView = [[WKWebView alloc] initWithFrame:CGRectZero configuration:configuration];
    self.webView.translatesAutoresizingMaskIntoConstraints = NO;
    self.webView.navigationDelegate = self;

    UIView *toolbar = [self makeToolbar];
    toolbar.translatesAutoresizingMaskIntoConstraints = NO;

    self.statusLabel = [[UILabel alloc] init];
    self.statusLabel.translatesAutoresizingMaskIntoConstraints = NO;
    self.statusLabel.font = [UIFont systemFontOfSize:12 weight:UIFontWeightRegular];
    self.statusLabel.textColor = UIColor.secondaryLabelColor;
    self.statusLabel.numberOfLines = 1;
    self.statusLabel.text = @"Paste a URL to load web content.";

    [self.view addSubview:toolbar];
    [self.view addSubview:self.statusLabel];
    [self.view addSubview:self.webView];

    UILayoutGuide *safeArea = self.view.safeAreaLayoutGuide;
    [NSLayoutConstraint activateConstraints:@[
        [toolbar.topAnchor constraintEqualToAnchor:safeArea.topAnchor],
        [toolbar.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [toolbar.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [toolbar.heightAnchor constraintEqualToConstant:48],

        [self.statusLabel.topAnchor constraintEqualToAnchor:toolbar.bottomAnchor],
        [self.statusLabel.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor constant:12],
        [self.statusLabel.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor constant:-12],
        [self.statusLabel.heightAnchor constraintEqualToConstant:24],

        [self.webView.topAnchor constraintEqualToAnchor:self.statusLabel.bottomAnchor],
        [self.webView.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [self.webView.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [self.webView.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor]
    ]];
}

- (void)dealloc
{
    [self.webView.configuration.userContentController removeScriptMessageHandlerForName:GreaseBridgeScriptMessageName];
}

- (UIView *)makeToolbar
{
    UIStackView *stack = [[UIStackView alloc] init];
    stack.axis = UILayoutConstraintAxisHorizontal;
    stack.alignment = UIStackViewAlignmentCenter;
    stack.distribution = UIStackViewDistributionFill;
    stack.spacing = 8;
    stack.layoutMargins = UIEdgeInsetsMake(6, 8, 6, 8);
    stack.layoutMarginsRelativeArrangement = YES;
    stack.backgroundColor = UIColor.secondarySystemBackgroundColor;

    [stack addArrangedSubview:[self buttonWithTitle:@"Close" action:@selector(closeTapped:)]];
    [stack addArrangedSubview:[self buttonWithTitle:@"Back" action:@selector(backTapped:)]];
    [stack addArrangedSubview:[self buttonWithTitle:@"Reload" action:@selector(reloadTapped:)]];
    [stack addArrangedSubview:[self buttonWithTitle:@"Paste URL" action:@selector(pasteURLTapped:)]];

    UIView *spacer = [[UIView alloc] init];
    [spacer setContentHuggingPriority:UILayoutPriorityDefaultLow forAxis:UILayoutConstraintAxisHorizontal];
    [stack addArrangedSubview:spacer];

    return stack;
}

- (UIButton *)buttonWithTitle:(NSString *)title action:(SEL)action
{
    UIButton *button = [UIButton buttonWithType:UIButtonTypeSystem];
    [button setTitle:title forState:UIControlStateNormal];
    button.titleLabel.font = [UIFont systemFontOfSize:15 weight:UIFontWeightSemibold];
    [button addTarget:self action:action forControlEvents:UIControlEventTouchUpInside];
    return button;
}

- (void)closeTapped:(id)sender
{
    [self willMoveToParentViewController:nil];
    [self.view removeFromSuperview];
    [self removeFromParentViewController];
}

- (void)backTapped:(id)sender
{
    if (self.webView.canGoBack) {
        [self.webView goBack];
    }
}

- (void)reloadTapped:(id)sender
{
    if (self.webView.URL) {
        [self.webView reload];
    }
}

- (void)pasteURLTapped:(id)sender
{
    NSString *pasteboardText = UIPasteboard.generalPasteboard.string;
    NSURL *url = [self normalizedURLFromString:pasteboardText];

    if (!url) {
        [self setStatus:@"Pasteboard does not contain a valid URL."];
        return;
    }

    [self setStatus:[NSString stringWithFormat:@"Loading %@", url.absoluteString]];
    [self.webView loadRequest:[NSURLRequest requestWithURL:url]];
}

- (NSURL *)normalizedURLFromString:(NSString *)text
{
    NSString *trimmed = [text stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
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

- (void)setStatus:(NSString *)status
{
    self.statusLabel.text = status;
}

#pragma mark - WKScriptMessageHandler

- (void)userContentController:(WKUserContentController *)userContentController didReceiveScriptMessage:(WKScriptMessage *)message
{
    if (![message.name isEqualToString:GreaseBridgeScriptMessageName]) {
        return;
    }

    NSDictionary *response = [self.bridge responseForScriptMessageBody:message.body];
    [self sendBridgeResponse:response];
}

- (void)sendBridgeResponse:(NSDictionary *)response
{
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
    NSString *script = [NSString stringWithFormat:@"window.Grease&&window.Grease._nativeCallback(%@);", json];
    [self.webView evaluateJavaScript:script completionHandler:nil];
}

#pragma mark - WKNavigationDelegate

- (void)webView:(WKWebView *)webView didFinishNavigation:(WKNavigation *)navigation
{
    [self setStatus:webView.URL.absoluteString ?: @"Loaded."];
}

- (void)webView:(WKWebView *)webView didFailNavigation:(WKNavigation *)navigation withError:(NSError *)error
{
    [self setStatus:error.localizedDescription ?: @"Navigation failed."];
}

- (void)webView:(WKWebView *)webView didFailProvisionalNavigation:(WKNavigation *)navigation withError:(NSError *)error
{
    [self setStatus:error.localizedDescription ?: @"Navigation failed."];
}

@end
