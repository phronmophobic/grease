(ns com.phronemophobic.grease.ios.whisper
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [tech.v3.datatype.ffi :as dt-ffi]))

(declare grease_whisper_start_dictation
         grease_whisper_stop_dictation
         grease_whisper_cancel_dictation
         grease_whisper_status
         grease_whisper_free_string)

(dt-ffi/define-library-interface
  {:grease_whisper_start_dictation
   {:rettype :pointer
    :argtypes [['model-path :string]]}

   :grease_whisper_stop_dictation
   {:rettype :pointer
    :argtypes []}

   :grease_whisper_cancel_dictation
   {:rettype :pointer
    :argtypes []}

   :grease_whisper_status
   {:rettype :pointer
    :argtypes []}

   :grease_whisper_free_string
   {:rettype :void
    :argtypes [['value :pointer]]}})

(defn- read-native-response [ptr]
  (when-not ptr
    (throw (ex-info "Whisper native call returned no response."
                    {})))
  (try
    (json/read-str (dt-ffi/c->string ptr))
    (finally
      (grease_whisper_free_string ptr))))

(defn- start-native [model-path]
  (read-native-response
   (grease_whisper_start_dictation model-path)))

(defn- stop-native []
  (read-native-response
   (grease_whisper_stop_dictation)))

(defn- cancel-native []
  (read-native-response
   (grease_whisper_cancel_dictation)))

(defn- status-native []
  (read-native-response
   (grease_whisper_status)))

(defn- unwrap-response [response]
  (if (true? (get response "ok"))
    response
    (throw (ex-info (or (get response "error")
                        "Whisper dictation failed.")
                    {:response response}))))

(defn start-dictation!
  "Starts microphone dictation with a caller-managed Whisper model.

  Options:
  `:model-path` local path to the ggml model file.

  The matching Core ML encoder must be next to the model using whisper.cpp's
  sibling convention, for example `model.bin` and `model-encoder.mlmodelc`."
  [{:keys [model-path]}]
  (let [model-path (some-> model-path str)]
    (when (str/blank? model-path)
      (throw (ex-info "start-dictation! requires :model-path."
                      {:model-path model-path})))
    (unwrap-response (start-native model-path))
    nil))

(defn dictation-status
  "Returns current dictation recording state and microphone levels."
  []
  (unwrap-response (status-native)))

(defn cancel-dictation!
  "Stops microphone dictation without transcribing the recording."
  []
  (unwrap-response (cancel-native))
  nil)

(defn stop-dictation!
  "Stops microphone dictation and returns the transcribed text."
  []
  (get (unwrap-response (stop-native))
       "transcript"
       ""))
