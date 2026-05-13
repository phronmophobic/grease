//
//  Bridge.h
//  MobileTest
//
//  Created by Adrian Smith on 6/7/21.
//

#ifndef Bridge_h
#define Bridge_h

#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

long long int call_sub(long long int a, long long int b);
long long int call_add(long long int a, long long int b);
void call_print(const char* s);
void call_prn(long long int id);
void call_start_server(void);
long long int call_eval(const char* s);
void call_print_hi(void);

long long int objc_msgSendU64(const char* s);
void* objc_make_string(const char* s);
void* objc_make_selector(const char* s);
#ifdef __cplusplus
}
#endif

#endif /* Bridge_h */
