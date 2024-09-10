//
//  MembraneView.m
//  TestSkia
//
//  Created by Adrian Smith on 6/13/21.
//

#import "MembraneView.h"
#include "bb.h"

static void* _clj_main_view;

const char* clj_app_dir(){
    NSBundle* mb = [NSBundle mainBundle];
    return [[mb bundlePath] UTF8String];
}
void* clj_main_view(){
    return _clj_main_view;
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
    _clj_main_view = (__bridge void*)self;
}

- (void) didMoveToWindow{
    [super didMoveToWindow];
    _clj_main_view = (__bridge void*)self;
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
