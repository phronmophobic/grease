//
//  AppDelegate.h
//  TestSkia
//
//  Created by Adrian Smith on 6/11/21.
//

#import <UIKit/UIKit.h>

@interface AppDelegate : UIResponder <UIApplicationDelegate>

@property (strong, nonatomic) UIWindow *window;

@end

#ifdef __cplusplus
extern "C" {
#endif

void GreaseRegisterDeepLinkIsolate(void *isolate);

#ifdef __cplusplus
}
#endif
