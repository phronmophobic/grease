(ns com.phronemophobic.grease.objc
  (:require [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype :as dtype]
            tech.v3.datatype.ffi.graalvm-runtime
            [com.phronemophobic.clj-libffi :as ffi]
            [com.phronemophobic.objcjure :refer [objc] :as objc]
            [tech.v3.datatype.native-buffer :as native-buffer]
            [clojure.java.io :as io])
  (:gen-class))

;; https://developer.apple.com/documentation/objectivec/objective-c_runtime?language=objc
;; https://developer.apple.com/tutorials/data/documentation/objectivec/objective-c_runtime.json?language=objc

;; blocks
;; https://www.galloway.me.uk/2012/10/a-look-inside-blocks-episode-1/

;; id objc_getClass(const char *name);

;; id class_createInstance(Class cls, size_t extraBytes);
;; Class NSClassFromString(NSString *aClassName);

(set! *warn-on-reflection* true)



(dt-ffi/define-library-interface
  {;; :objc_msgSend {:rettype :int64
   ;;                :argtypes [['obj :pointer]
   ;;                           ['sel :pointer]]}

   ;; :objc_make_selector {:rettype :pointer
   ;;                      :argtypes [['sel :pointer]]}
   ;; :objc_make_string {:rettype :pointer
   ;;                    :argtypes [['s :pointer]]}

   :xAcceleration {:rettype :float64
                   :argtypes [['data :pointer]]}
   :yAcceleration {:rettype :float64
                   :argtypes [['data :pointer]]}
   :zAcceleration {:rettype :float64
                   :argtypes [['data :pointer]]}

   :objc_getClass {:rettype :pointer
                   :argtypes [['classname :pointer]]}

   ;; :call_clj_fn {:rettype :void
   ;;               :argtypes [['fptr :pointer]]}
   :clj_main_view {:rettype :pointer
                   :argtypes []}

   :clj_app_dir {:rettype :pointer
                 :argtypes []}

   :clj_debug {:rettype :pointer
               :argtypes [['data :pointer]]}
   ,})

(comment

  ;; experimenting with potential
  ;; objc interop syntax
  (def CMMotionManager (class 'CMMotionManager))
  ;; (objc NSString +stringWithFormat:)

  (let [motion ((alloc CMMotionManager) :init)]
    (when (motion :accelerometerAvailable)
      )))



(def UIAlertControllerStyleActionSheet 0)
(def UIAlertControllerStyleAlert 1)

(def UIAlertActionStyleDefault 0)
(def UIAlertActionStyleCancel 1)
(def UIAlertActionStyleDestructive 2)

(def main-queue (delay (ffi/dlsym ffi/RTLD_DEFAULT (dt-ffi/string->c "_dispatch_main_q"))))

(defn NSSetUncaughtExceptionHandler [handler]
  (ffi/call "NSSetUncaughtExceptionHandler" :void :pointer handler)
  )

(defn dispatch-main-async [f]
  (ffi/call "dispatch_async" :void :pointer @main-queue :pointer (objc
                                                                  (fn ^void []
                                                                    (try
                                                                      (f)
                                                                      (catch Exception e
                                                                        (println e)))))))

#_(defn show-alert! []
    (dispatch-main-async
     (fn []
       (let [vc (objc [[[UIApplication :sharedApplication] :keyWindow] :rootViewController])
             alert (objc
                    [UIAlertController :alertControllerWithTitle:message:preferredStyle
                     @"Title"
                     @"Message"
                     UIAlertControllerStyleAlert])
             default-action (objc [UIAlertAction :actionWithTitle:style:handler
                                   @"default"
                                   UIAlertActionStyleDefault
                                   (fn ^void [action]
                                     (log :default-action))])]
         (objc [alert :addAction default-action])
         (objc [vc :presentViewController:animated:completion
                alert true nil])))))

(defn show-keyboard []
  (objc ^void
        [~(clj_main_view) :performSelectorOnMainThread:withObject:waitUntilDone
         ~(objc/sel_registerName (dt-ffi/string->c "becomeFirstResponder"))
         nil
         ~(byte 0)]))

(defn hide-keyboard []
  (objc ^void
        [~(clj_main_view) :performSelectorOnMainThread:withObject:waitUntilDone
         ~(objc/sel_registerName (dt-ffi/string->c "resignFirstResponder"))
         nil
         ~(byte 0)]))


(defn documents-dir []
  ;; fileSystemRepresentation
  (io/file
   (dt-ffi/c->string
    (objc [[[[NSFileManager defaultManager] :URLsForDirectory:inDomains
             ;; (int 14) ;; application support
             (int 9) ;; documents
             (int 1)
             ]
            :objectAtIndex 0]
           fileSystemRepresentation])))
  )
