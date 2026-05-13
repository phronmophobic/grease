#import <Foundation/Foundation.h>

#ifdef __cplusplus
extern "C" {
#endif

char *grease_whisper_start_dictation(const char *model_path);
char *grease_whisper_stop_dictation(void);
void grease_whisper_free_string(char *value);

#ifdef __cplusplus
}
#endif
