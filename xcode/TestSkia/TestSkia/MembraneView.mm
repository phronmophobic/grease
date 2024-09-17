//
//  MembraneView.m
//  TestSkia
//
//  Created by Adrian Smith on 6/13/21.
//

#import "MembraneView.h"
#include "bb.h"

// These are here as a convenience for looking up
// objc enums and types.
#import <AVFoundation/AVFoundation.h>
#import <CoreMedia/CoreMedia.h>
#import <MediaPlayer/MediaPlayer.h>
#import <HealthKit/HealthKit.h>

static MembraneView* _clj_main_view;

void set_main_view(MembraneView* view){
    _clj_main_view = view;
}

const char* clj_app_dir(){
    NSBundle* mb = [NSBundle mainBundle];
    return [[mb bundlePath] UTF8String];
}
void* clj_main_view(){
    return (__bridge void*)_clj_main_view;
}

void clj_debug(void* p){
//    AVPlayer* player = (__bridge AVPlayer*)p;
////    CMTime t = [[player currentItem] duration];
//    CMTime t =
//    NSLog(@"got time %f" ,((double)t.value /  t.timescale));
}

void clj_generic_callback(void *cif, void *ret, void* args,
                    void *userdata)
{
    long long int key = *((long long int *)userdata);
    
    graal_isolate_t *isolate = _clj_main_view.isolate;
    
    graal_isolatethread_t* thread;
    graal_attach_thread(isolate, &thread);
    com_phronemophobic_clj_libffi_callback(thread , key, ret, args);
	graal_detach_thread(thread);
}


operation_t clj_get_generic_callback_address(){
    return &clj_generic_callback;
}

double xAcceleration(CMAccelerometerData* data){
    return data.acceleration.x;
}
double yAcceleration(CMAccelerometerData* data){
    return data.acceleration.y;
}
double zAcceleration(CMAccelerometerData* data){
    return data.acceleration.z;
}

@implementation MembraneView

- (BOOL) canBecomeFirstResponder{
    return YES;
}
/*
// Only override drawRect: if you perform custom drawing.
// An empty implementation adversely affects performance during animation.
- (void)drawRect:(CGRect)rect {
    // Drawing code
}
*/

-(void)touchesEnded:(NSSet<UITouch *> *)touches withEvent:(UIEvent *)event{
    graal_isolatethread_t* thread;
    graal_attach_thread(self.isolate, &thread);
    for (UITouch* t: [event allTouches]){
        CGPoint pt = [t locationInView:self];
        clj_touch_ended(thread, pt.x, pt.y);
    }
	graal_detach_thread(thread);
    
}

- (void)touchesBegan:(NSSet<UITouch *> *)touches
           withEvent:(UIEvent *)event{
    graal_isolatethread_t* thread;
    graal_attach_thread(self.isolate, &thread);
    for (UITouch* t: [event allTouches]){
        CGPoint pt = [t locationInView:self];
        clj_touch_began(thread, pt.x, pt.y);
    }
	graal_detach_thread(thread);
}

- (void)touchesMoved:(NSSet<UITouch *> *)touches
           withEvent:(UIEvent *)event{
    graal_isolatethread_t* thread;
    graal_attach_thread(self.isolate, &thread);
    for (UITouch* t: [event allTouches]){
        CGPoint pt = [t locationInView:self];
        clj_touch_moved(thread, pt.x, pt.y);
    }
	graal_detach_thread(thread);
}

- (void)touchesCancelled:(NSSet<UITouch *> *)touches
               withEvent:(UIEvent *)event{}

- (void) didMoveToSuperview{
    [super didMoveToSuperview];
//    _clj_main_view = (__bridge void*)self;
}

- (void) didMoveToWindow{
    [super didMoveToWindow];
//    _clj_main_view = (__bridge void*)self;
}

- (void)deleteBackward{
    graal_isolatethread_t* thread;
    graal_attach_thread(self.isolate, &thread);
    clj_delete_backward(thread);
	graal_detach_thread(thread);
}

- (void)insertText:(NSString *)text{
    graal_isolatethread_t* thread;
    graal_attach_thread(self.isolate, &thread);
    clj_insert_text(thread, (void*)[text UTF8String]);
	graal_detach_thread(thread);
}

- (BOOL) hasText{
    return NO;
}
@end
