//
//  MembraneView.m
//  TestSkia
//
//  Created by Adrian Smith on 6/13/21.
//

#import "MembraneView.h"
#include "bb.h"


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

void clj_generic_callback(void *cif, void *ret, void* args,
                    void *userdata)
{
    long long int key = *((long long int *)userdata);
    graal_isolatethread_t* thread =_clj_main_view.thread;
    com_phronemophobic_clj_libffi_callback(thread , key, ret, args);
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
    for (UITouch* t: [event allTouches]){
        CGPoint pt = [t locationInView:self];
        clj_touch_ended(self.thread, pt.x, pt.y);
    }
}

- (void)touchesBegan:(NSSet<UITouch *> *)touches
           withEvent:(UIEvent *)event{
    for (UITouch* t: [event allTouches]){
        CGPoint pt = [t locationInView:self];
        clj_touch_began(self.thread, pt.x, pt.y);
    }
}

- (void)touchesMoved:(NSSet<UITouch *> *)touches
           withEvent:(UIEvent *)event{
    for (UITouch* t: [event allTouches]){
        CGPoint pt = [t locationInView:self];
        clj_touch_moved(self.thread, pt.x, pt.y);
    }
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
    clj_delete_backward(self.thread);
}

- (void)insertText:(NSString *)text{
    clj_insert_text(self.thread, (void*)[text UTF8String]);
}

- (BOOL) hasText{
    return NO;
}
@end
