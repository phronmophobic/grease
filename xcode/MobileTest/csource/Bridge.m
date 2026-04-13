//
//  Bridge.c
//  MobileTest
//
//  Created by Adrian Smith on 6/7/21.
//

#include "Bridge.h"


//#include "mobiletest-uber.h"
#include "bb.h"
#import <Foundation/Foundation.h>
#include <objc/message.h>
//#import "objc-runtime.h"


graal_isolate_t *isolate = NULL;
graal_isolatethread_t *thread = NULL;

long long int call_sub(long long int a, long long int b){

    if ( !isolate ){
      if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
        fprintf(stderr, "initialization error\n");
        return 1;
      }
    }

  return clj_sub(thread, a, b);
}


long long int call_add(long long int a, long long int b){

    if ( !isolate ){
      if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
        fprintf(stderr, "initialization error\n");
        return 1;
      }
    }

  return clj_add(thread, a, b);
}

void call_print(const char* s){
    
    if ( !isolate ){
      if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
        fprintf(stderr, "initialization error\n");
        return;
      }
    }

  clj_print(thread,(void*)s);
}
void call_print_hi(void){
    if ( !isolate ){
      if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
        fprintf(stderr, "initialization error\n");
        return;
      }
    }

  clj_print_hi(thread);
}

long long int call_eval(const char* s){
    
    if ( !isolate ){
      if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
        fprintf(stderr, "initialization error\n");
        return -1;
      }
    }

    return clj_eval(thread,(void*)s);
}

void call_prn(long long int id){
    
    if ( !isolate ){
      if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
        fprintf(stderr, "initialization error\n");
        return;
      }
    }

  clj_prn(thread,id);
}

void call_start_server(void){
    
    if ( !isolate ){
      if (graal_create_isolate(NULL, &isolate, &thread) != 0) {
        fprintf(stderr, "initialization error\n");
        return;
      }
    }

  clj_start_server(thread);
}



void testme(void){
    
    
}
    
long long int objc_msgSendU64(const char* s){
    NSString* ss = @"asdfasdf";
    SEL sel = NSSelectorFromString([NSString stringWithUTF8String:s]);

    return  ((NSUInteger (*)(id, SEL))objc_msgSend)(ss, sel);
}


void* objc_make_selector(const char* s){
    
    SEL sel = NSSelectorFromString([NSString stringWithUTF8String:s]);
    return (void*)sel;

}

void* objc_make_string(const char* s){
    return (__bridge void *)([[NSString alloc] initWithUTF8String:s]);
}

// function pointer
void (*fn_ptr)(graal_isolatethread_t*);
void call_clj_fn(void (*clj_fn_ptr)(graal_isolatethread_t*)){
        fn_ptr = clj_fn_ptr;
    fn_ptr(thread);
}



