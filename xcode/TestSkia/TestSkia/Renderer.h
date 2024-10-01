//
//  Renderer.h
//  TestSkia
//
//  Created by Adrian Smith on 6/11/21.
//

#import <MetalKit/MetalKit.h>
#import "bb.h"

// Our platform independent renderer class.   Implements the MTKViewDelegate protocol which
//   allows it to accept per-frame update and drawable resize callbacks.
@interface Renderer : NSObject <MTKViewDelegate>

-(nonnull instancetype)initWithMetalKitView:(nonnull MTKView *)view;

@property (assign, atomic) graal_isolate_t *isolate;

@end

