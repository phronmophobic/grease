#import "GreaseWhisper.h"

#import <AVFoundation/AVFoundation.h>
#import <whisper/whisper.h>

#include <string.h>
#include <algorithm>
#include <string>
#include <vector>

@interface GreaseWhisperDictation : NSObject <AVAudioRecorderDelegate>

- (NSDictionary *)startWithModelPath:(NSString *)modelPath;
- (NSDictionary *)stop;

@end

@implementation GreaseWhisperDictation {
  AVAudioRecorder *_recorder;
  NSURL *_recordingURL;
  struct whisper_context *_context;
  NSString *_contextModelPath;
}

- (void)dealloc {
  if (_context) {
    whisper_free(_context);
  }
}

- (NSDictionary *)startWithModelPath:(NSString *)modelPath {
  if (_recorder) {
    return [self error:@"Dictation is already recording"];
  }

  NSString *trimmedModelPath =
      [modelPath stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
  if (trimmedModelPath.length == 0) {
    return [self error:@"start-dictation! requires :model-path"];
  }

  if (![NSFileManager.defaultManager fileExistsAtPath:trimmedModelPath]) {
    return [self
        error:[NSString stringWithFormat:@"Whisper model was not found: %@", trimmedModelPath]];
  }

  NSString *encoderPath = [self encoderPathForModelPath:trimmedModelPath];
  BOOL isDirectory = NO;
  if (![NSFileManager.defaultManager fileExistsAtPath:encoderPath isDirectory:&isDirectory] ||
      !isDirectory) {
    return [self error:[NSString stringWithFormat:@"Whisper Core ML encoder was not found: %@",
                                                  encoderPath]];
  }

  NSDictionary *contextResult = [self loadContextForModelPath:trimmedModelPath];
  if (![contextResult[@"ok"] boolValue]) {
    return contextResult;
  }

  NSDictionary *permissionResult = [self requestRecordPermission];
  if (![permissionResult[@"ok"] boolValue]) {
    return permissionResult;
  }

  NSError *error = nil;
  AVAudioSession *session = AVAudioSession.sharedInstance;
  [session setCategory:AVAudioSessionCategoryPlayAndRecord
                  mode:AVAudioSessionModeDefault
               options:AVAudioSessionCategoryOptionDefaultToSpeaker
                 error:&error];
  if (error) {
    return [self error:[NSString stringWithFormat:@"Could not configure audio session: %@",
                                                  error.localizedDescription]];
  }

  [session setActive:YES error:&error];
  if (error) {
    return [self error:[NSString stringWithFormat:@"Could not activate audio session: %@",
                                                  error.localizedDescription]];
  }

  _recordingURL =
      [NSURL fileURLWithPath:[NSTemporaryDirectory()
                                 stringByAppendingPathComponent:@"grease-dictation.wav"]];
  NSDictionary *settings = @{
    AVFormatIDKey : @(kAudioFormatLinearPCM),
    AVSampleRateKey : @16000.0,
    AVNumberOfChannelsKey : @1,
    AVLinearPCMBitDepthKey : @16,
    AVLinearPCMIsFloatKey : @NO,
    AVLinearPCMIsBigEndianKey : @NO
  };

  _recorder = [[AVAudioRecorder alloc] initWithURL:_recordingURL settings:settings error:&error];
  if (error || !_recorder) {
    _recorder = nil;
    return [self error:[NSString stringWithFormat:@"Could not create audio recorder: %@",
                                                  error.localizedDescription ?: @"unknown error"]];
  }

  _recorder.delegate = self;
  if (![_recorder record]) {
    _recorder = nil;
    return [self error:@"Could not start audio recording"];
  }

  return @{@"ok" : @YES};
}

- (NSDictionary *)stop {
  if (!_recorder || !_recordingURL) {
    return [self error:@"Dictation is not recording"];
  }

  NSURL *recordingURL = _recordingURL;
  [_recorder stop];
  _recorder = nil;
  _recordingURL = nil;
  [AVAudioSession.sharedInstance setActive:NO
                               withOptions:AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation
                                     error:nil];

  if (!_context) {
    return [self error:@"Whisper context is not loaded"];
  }

  NSError *decodeError = nil;
  std::vector<float> samples = [self decodeSamplesAtURL:recordingURL error:&decodeError];
  if (decodeError) {
    return [self error:[NSString stringWithFormat:@"Could not decode recording: %@",
                                                  decodeError.localizedDescription]];
  }

  if (samples.empty()) {
    return [self error:@"Recording did not contain audio samples"];
  }

  NSString *transcript = [self transcribeSamples:samples];
  if (!transcript) {
    return [self error:@"Whisper transcription failed"];
  }

  NSString *trimmedTranscript =
      [transcript stringByTrimmingCharactersInSet:NSCharacterSet.whitespaceAndNewlineCharacterSet];
  return @{@"ok" : @YES, @"transcript" : trimmedTranscript ?: @""};
}

- (NSDictionary *)loadContextForModelPath:(NSString *)modelPath {
  if (_context && [_contextModelPath isEqualToString:modelPath]) {
    return @{@"ok" : @YES};
  }

  if (_context) {
    whisper_free(_context);
    _context = nullptr;
    _contextModelPath = nil;
  }

  struct whisper_context_params params = whisper_context_default_params();
#if TARGET_OS_SIMULATOR
  params.use_gpu = false;
#else
  params.flash_attn = true;
#endif

  _context = whisper_init_from_file_with_params(modelPath.UTF8String, params);
  if (!_context) {
    return [self error:@"Could not initialize Whisper context"];
  }

  _contextModelPath = [modelPath copy];
  return @{@"ok" : @YES};
}

- (NSDictionary *)requestRecordPermission {
  AVAudioSession *session = AVAudioSession.sharedInstance;
  AVAudioSessionRecordPermission permission = session.recordPermission;

  if (permission == AVAudioSessionRecordPermissionGranted) {
    return @{@"ok" : @YES};
  }

  if (permission == AVAudioSessionRecordPermissionDenied) {
    return [self error:@"Microphone permission is required"];
  }

  dispatch_semaphore_t semaphore = dispatch_semaphore_create(0);
  __block BOOL granted = NO;
  [session requestRecordPermission:^(BOOL didGrant) {
    granted = didGrant;
    dispatch_semaphore_signal(semaphore);
  }];
  dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER);

  if (!granted) {
    return [self error:@"Microphone permission is required"];
  }

  return @{@"ok" : @YES};
}

- (std::vector<float>)decodeSamplesAtURL:(NSURL *)url error:(NSError **)error {
  AVAudioFile *file = [[AVAudioFile alloc] initForReading:url error:error];
  if (!file) {
    return {};
  }

  AVAudioFormat *sourceFormat = file.processingFormat;
  AVAudioFormat *targetFormat = [[AVAudioFormat alloc] initWithCommonFormat:AVAudioPCMFormatFloat32
                                                                 sampleRate:16000
                                                                   channels:1
                                                                interleaved:NO];
  if (!targetFormat) {
    if (error) {
      *error =
          [NSError errorWithDomain:@"GreaseWhisper"
                              code:1
                          userInfo:@{
                            NSLocalizedDescriptionKey : @"Could not create 16 kHz mono audio format"
                          }];
    }
    return {};
  }

  AVAudioConverter *converter = [[AVAudioConverter alloc] initFromFormat:sourceFormat
                                                                toFormat:targetFormat];
  if (!converter) {
    if (error) {
      *error = [NSError
          errorWithDomain:@"GreaseWhisper"
                     code:2
                 userInfo:@{NSLocalizedDescriptionKey : @"Could not create audio converter"}];
    }
    return {};
  }

  AVAudioFrameCount inputFrameCapacity = (AVAudioFrameCount)file.length;
  AVAudioPCMBuffer *inputBuffer = [[AVAudioPCMBuffer alloc] initWithPCMFormat:sourceFormat
                                                                frameCapacity:inputFrameCapacity];
  if (!inputBuffer || ![file readIntoBuffer:inputBuffer error:error]) {
    return {};
  }

  double ratio = targetFormat.sampleRate / sourceFormat.sampleRate;
  AVAudioFrameCount outputFrameCapacity = (AVAudioFrameCount)(inputBuffer.frameLength * ratio) + 1;
  AVAudioPCMBuffer *outputBuffer = [[AVAudioPCMBuffer alloc] initWithPCMFormat:targetFormat
                                                                 frameCapacity:outputFrameCapacity];
  if (!outputBuffer) {
    if (error) {
      *error = [NSError
          errorWithDomain:@"GreaseWhisper"
                     code:3
                 userInfo:@{NSLocalizedDescriptionKey : @"Could not allocate output buffer"}];
    }
    return {};
  }

  __block BOOL didProvideInput = NO;
  NSError *conversionError = nil;
  [converter convertToBuffer:outputBuffer
                       error:&conversionError
          withInputFromBlock:^AVAudioBuffer *_Nullable(AVAudioPacketCount inNumberOfPackets,
                                                       AVAudioConverterInputStatus *outStatus) {
            if (didProvideInput) {
              *outStatus = AVAudioConverterInputStatus_NoDataNow;
              return nil;
            }

            didProvideInput = YES;
            *outStatus = AVAudioConverterInputStatus_HaveData;
            return inputBuffer;
          }];

  if (conversionError) {
    if (error) {
      *error = conversionError;
    }
    return {};
  }

  float *channelData = outputBuffer.floatChannelData ? outputBuffer.floatChannelData[0] : nullptr;
  if (!channelData || outputBuffer.frameLength == 0) {
    return {};
  }

  return std::vector<float>(channelData, channelData + outputBuffer.frameLength);
}

- (NSString *)transcribeSamples:(const std::vector<float> &)samples {
  struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
  int processorCount = (int)NSProcessInfo.processInfo.processorCount;
  params.print_realtime = false;
  params.print_progress = false;
  params.print_timestamps = false;
  params.print_special = false;
  params.translate = false;
  params.language = "en";
  params.n_threads = std::max(1, std::min(8, processorCount - 2));
  params.offset_ms = 0;
  params.no_context = true;
  params.single_segment = false;

  whisper_reset_timings(_context);
  int result = whisper_full(_context, params, samples.data(), (int)samples.size());
  if (result != 0) {
    return nil;
  }

  std::string text;
  int segmentCount = whisper_full_n_segments(_context);
  for (int index = 0; index < segmentCount; index++) {
    const char *segment = whisper_full_get_segment_text(_context, index);
    if (segment) {
      text += segment;
    }
  }

  return [NSString stringWithUTF8String:text.c_str()] ?: @"";
}

- (NSString *)encoderPathForModelPath:(NSString *)modelPath {
  NSString *withoutExtension = [modelPath stringByDeletingPathExtension];
  NSString *encoderName =
      [[withoutExtension lastPathComponent] stringByAppendingString:@"-encoder.mlmodelc"];
  return [[withoutExtension stringByDeletingLastPathComponent]
      stringByAppendingPathComponent:encoderName];
}

- (NSDictionary *)error:(NSString *)message {
  return @{@"ok" : @NO, @"error" : message ?: @"Unknown Whisper error"};
}

@end

static GreaseWhisperDictation *GreaseWhisperSharedDictation(void) {
  static GreaseWhisperDictation *dictation = nil;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    dictation = [[GreaseWhisperDictation alloc] init];
  });
  return dictation;
}

static char *GreaseWhisperCopyJSON(NSDictionary *response) {
  NSError *error = nil;
  NSData *data = [NSJSONSerialization dataWithJSONObject:response options:0 error:&error];
  if (!data) {
    NSString *fallback =
        [NSString stringWithFormat:@"{\"ok\":false,\"error\":\"%@\"}",
                                   error.localizedDescription ?: @"Could not encode response"];
    return strdup(fallback.UTF8String);
  }

  NSString *json = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
  return strdup(json.UTF8String);
}

char *grease_whisper_start_dictation(const char *model_path) {
  @autoreleasepool {
    NSString *path = model_path ? [NSString stringWithUTF8String:model_path] : @"";
    return GreaseWhisperCopyJSON([GreaseWhisperSharedDictation() startWithModelPath:path]);
  }
}

char *grease_whisper_stop_dictation(void) {
  @autoreleasepool {
    return GreaseWhisperCopyJSON([GreaseWhisperSharedDictation() stop]);
  }
}

void grease_whisper_free_string(char *value) { free(value); }
