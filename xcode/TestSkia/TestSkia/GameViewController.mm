//
//  GameViewController.m
//  TestSkia
//
//  Created by Adrian Smith on 6/11/21.
//

#import "GameViewController.h"
#import "Renderer.h"
#import "bb.h"
#import "MembraneView.h"
#import "GreaseWebOverlayController.h"

@implementation GameViewController
{
    MembraneView *_view;

    Renderer *_renderer;
    GreaseWebOverlayController *_webOverlayController;
    UIButton *_webButton;
    graal_isolate_t *isolate;
	BOOL initialized;
}

- (void)viewDidLoad
{
    [super viewDidLoad];

    _view = (MembraneView*)self.view;
    _view.multipleTouchEnabled = YES;

    _view.device = MTLCreateSystemDefaultDevice();
    _view.backgroundColor = UIColor.whiteColor;

    if(!_view.device)
    {
        NSLog(@"Metal is not supported on this device");
        self.view = [[UIView alloc] initWithFrame:self.view.frame];
        [self installWebButton];
        return;
    }
    

	if (graal_create_isolate(NULL, &isolate, NULL) != 0) {
		fprintf(stderr, "initialization error\n");
	}

	_view.isolate = isolate;
    _renderer = [[Renderer alloc] initWithMetalKitView:_view];
	_renderer.isolate = isolate;

    [_renderer mtkView:_view drawableSizeWillChange:_view.bounds.size];

    _view.delegate = _renderer;

    [self installWebButton];
}

- (void) viewDidAppear:(BOOL)animated{
    [super viewDidAppear:animated];
    
	if ( !initialized ){

		graal_isolatethread_t* thread;
		graal_attach_thread(isolate, &thread);
		clj_init(thread);
		graal_detach_thread(thread);
		initialized = YES;
	}
}

- (void)installWebButton
{
    if (_webButton != nil) {
        return;
    }

    _webButton = [UIButton buttonWithType:UIButtonTypeSystem];
    [_webButton setTitle:@"Web" forState:UIControlStateNormal];
    _webButton.titleLabel.font = [UIFont systemFontOfSize:16 weight:UIFontWeightSemibold];
    _webButton.backgroundColor = UIColor.systemBackgroundColor;
    _webButton.layer.cornerRadius = 8;
    _webButton.contentEdgeInsets = UIEdgeInsetsMake(8, 12, 8, 12);
    _webButton.translatesAutoresizingMaskIntoConstraints = NO;
    [_webButton addTarget:self action:@selector(showWebOverlay:) forControlEvents:UIControlEventTouchUpInside];

    [self.view addSubview:_webButton];
    [NSLayoutConstraint activateConstraints:@[
        [_webButton.topAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.topAnchor constant:12],
        [_webButton.trailingAnchor constraintEqualToAnchor:self.view.safeAreaLayoutGuide.trailingAnchor constant:-12]
    ]];
}

- (void)showWebOverlay:(id)sender
{
    if (_webOverlayController.parentViewController != nil) {
        [self.view bringSubviewToFront:_webOverlayController.view];
        return;
    }

    _webOverlayController = [[GreaseWebOverlayController alloc] init];
    [self addChildViewController:_webOverlayController];
    _webOverlayController.view.translatesAutoresizingMaskIntoConstraints = NO;
    [self.view addSubview:_webOverlayController.view];

    [NSLayoutConstraint activateConstraints:@[
        [_webOverlayController.view.topAnchor constraintEqualToAnchor:self.view.topAnchor],
        [_webOverlayController.view.leadingAnchor constraintEqualToAnchor:self.view.leadingAnchor],
        [_webOverlayController.view.trailingAnchor constraintEqualToAnchor:self.view.trailingAnchor],
        [_webOverlayController.view.bottomAnchor constraintEqualToAnchor:self.view.bottomAnchor]
    ]];

    [_webOverlayController didMoveToParentViewController:self];
}
@end
