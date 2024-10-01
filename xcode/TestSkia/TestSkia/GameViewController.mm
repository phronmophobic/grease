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

@implementation GameViewController
{
    MembraneView *_view;

    Renderer *_renderer;
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
@end
