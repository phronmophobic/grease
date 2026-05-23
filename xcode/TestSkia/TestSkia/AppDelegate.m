//
//  AppDelegate.m
//  TestSkia
//
//  Created by Adrian Smith on 6/11/21.
//

#import "AppDelegate.h"
#import "bb.h"

static graal_isolate_t *GreaseDeepLinkIsolate = NULL;

static NSMutableArray<NSString *> *GreasePendingDeepLinkURLs(void) {
  static NSMutableArray<NSString *> *pendingURLs;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    pendingURLs = [NSMutableArray array];
  });
  return pendingURLs;
}

static void GreaseHandleDeepLinkURLString(NSString *urlString) {
  if (urlString.length == 0) {
    return;
  }

  if (!GreaseDeepLinkIsolate) {
    [GreasePendingDeepLinkURLs() addObject:[urlString copy]];
    return;
  }

  graal_isolatethread_t *thread;
  graal_attach_thread(GreaseDeepLinkIsolate, &thread);
  int handled = clj_handle_deep_link(thread, (void *)urlString.UTF8String);
  graal_detach_thread(thread);

  if (!handled) {
    NSLog(@"Grease failed to handle deep link: %@", urlString);
  }
}

void GreaseRegisterDeepLinkIsolate(void *isolate) {
  GreaseDeepLinkIsolate = (graal_isolate_t *)isolate;

  NSArray<NSString *> *pendingURLs = [GreasePendingDeepLinkURLs() copy];
  [GreasePendingDeepLinkURLs() removeAllObjects];

  for (NSString *urlString in pendingURLs) {
    GreaseHandleDeepLinkURLString(urlString);
  }
}

@interface AppDelegate ()

@end

@implementation AppDelegate

- (BOOL)application:(UIApplication *)application
    didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
  NSURL *launchURL = launchOptions[UIApplicationLaunchOptionsURLKey];
  if ([launchURL isKindOfClass:NSURL.class]) {
    GreaseHandleDeepLinkURLString(launchURL.absoluteString);
  }
  return YES;
}

- (BOOL)application:(UIApplication *)application
            openURL:(NSURL *)url
            options:(NSDictionary<UIApplicationOpenURLOptionsKey, id> *)options {
  GreaseHandleDeepLinkURLString(url.absoluteString);
  return YES;
}

- (void)applicationWillResignActive:(UIApplication *)application {
  // Sent when the application is about to move from active to inactive state. This can occur for
  // certain types of temporary interruptions (such as an incoming phone call or SMS message) or
  // when the user quits the application and it begins the transition to the background state. Use
  // this method to pause ongoing tasks, disable timers, and invalidate graphics rendering
  // callbacks. Games should use this method to pause the game.
}

- (void)applicationDidEnterBackground:(UIApplication *)application {
  // Use this method to release shared resources, save user data, invalidate timers, and store
  // enough application state information to restore your application to its current state in case
  // it is terminated later.
}

- (void)applicationWillEnterForeground:(UIApplication *)application {
  // Called as part of the transition from the background to the active state; here you can undo
  // many of the changes made on entering the background.
}

- (void)applicationDidBecomeActive:(UIApplication *)application {
  // Restart any tasks that were paused (or not yet started) while the application was inactive. If
  // the application was previously in the background, optionally refresh the user interface.
}

@end
