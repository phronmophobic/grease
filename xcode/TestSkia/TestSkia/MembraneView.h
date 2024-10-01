//
//  MembraneView.h
//  TestSkia
//
//  Created by Adrian Smith on 6/13/21.
//

#import <MetalKit/MetalKit.h>
#import "bb.h"
#import "skia.h"
#import <UIKit/UIKit.h>
#import <CoreMotion/CoreMotion.h>

typedef void (*operation_t)(void*,void*,void*,void*);

extern "C" {
const char* _Nullable clj_app_dir();
void* _Nullable clj_main_view();
void clj_generic_callback(void * _Nullable cif, void * _Nullable ret, void* _Nullable args,
                          void * _Nullable userdata);
operation_t clj_get_generic_callback_address();
void clj_debug(void* p);


}

NS_ASSUME_NONNULL_BEGIN

@interface MembraneView : MTKView <UIKeyInput>{
    
}

@property (assign, atomic) graal_isolate_t *isolate;

@end

void set_main_view(MembraneView* view);

NS_ASSUME_NONNULL_END
