(ns com.phronemophobic.grease.objc
  (:require [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype :as dtype]
            tech.v3.datatype.ffi.graalvm-runtime
            [tech.v3.datatype.native-buffer :as native-buffer])
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


