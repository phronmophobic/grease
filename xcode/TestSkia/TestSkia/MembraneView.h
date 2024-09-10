//
//  MembraneView.h
//  TestSkia
//
//  Created by Adrian Smith on 6/13/21.
//

#import <MetalKit/MetalKit.h>
#include "bb.h"
#include "skia.h"
#import <UIKit/UIKit.h>
#import <CoreMotion/CoreMotion.h>

extern "C" {
const char* _Nullable clj_app_dir();
void* clj_main_view();

double xAcceleration(CMAccelerometerData* _Nonnull data);
double yAcceleration(CMAccelerometerData* _Nonnull data);
double zAcceleration(CMAccelerometerData* _Nonnull data);

}

NS_ASSUME_NONNULL_BEGIN

@interface MembraneView : MTKView <UIKeyInput>{
    
}

@property (assign, nonatomic) graal_isolate_t *isolate;
@property (assign, nonatomic) graal_isolatethread_t *thread;

@end

NS_ASSUME_NONNULL_END
