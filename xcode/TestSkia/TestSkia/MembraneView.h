//
//  MembraneView.h
//  TestSkia
//
//  Created by Adrian Smith on 6/13/21.
//

#import <MetalKit/MetalKit.h>
#include "mobiletest-uber.h"
#include "skia.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface MembraneView : MTKView <UIKeyInput>{
    
}

@property (assign, nonatomic) graal_isolate_t *isolate;
@property (assign, nonatomic) graal_isolatethread_t *thread;

@end

NS_ASSUME_NONNULL_END