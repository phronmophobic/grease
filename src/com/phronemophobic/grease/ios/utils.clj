(ns com.phronemophobic.grease.ios.utils
  (:require [com.phronemophobic.clj-libffi :as ffi]
            [com.phronemophobic.objcjure :as objc :refer [objc]]
            [tech.v3.datatype.ffi :as dt-ffi]))

(def nil-pointer
  "A nil Objective-C object pointer."
  (ffi/long->pointer 0))

(defn nil-pointer? [x]
  (or (nil? x)
      (try
        (zero? (.address (dt-ffi/->pointer x)))
        (catch Throwable _
          false))))

(def main-queue
  (delay
    (ffi/dlsym ffi/RTLD_DEFAULT (dt-ffi/string->c "_dispatch_main_q"))))

(defn dispatch-main-async
  "Run `f` on the main queue."
  [f]
  (ffi/call "dispatch_async" :void :pointer @main-queue :pointer
            (objc
             (fn ^void []
               (try
                 (f)
                 (catch Exception e
                   (println e)))))))

(defmacro on-main
  "Synchronously runs `body` on the main thread and returns the result."
  [& body]
  `(if (objc ~(with-meta '[NSThread isMainThread]
                {:tag 'boolean}))
     (do ~@body)
     (let [p# (promise)]
       (dispatch-main-async
        (fn []
          (let [ret# (do ~@body)]
            (deliver p# ret#))))
       @p#)))

(defn nullable-nsstring [s]
  (if (nil? s)
    nil-pointer
    (objc/str->nsstring (str s))))

(defn nsnumber-bool [x]
  (objc [NSNumber :numberWithBool ~(byte (if x 1 0))]))

(defn native-dictionary [m]
  (let [dict (objc [NSMutableDictionary :dictionary])]
    (doseq [[k v] m]
      (objc ^void [dict :setObject:forKey
                   ~(if (map? v)
                      (native-dictionary v)
                      (nsnumber-bool true))
                   ~(objc/str->nsstring k)]))
    dict))

(defn NSString->str [s]
  (when-not (nil-pointer? s)
    (objc/nsstring->str s)))

(defn NSURL->str [url]
  (when-not (nil-pointer? url)
    (NSString->str (objc [url :absoluteString]))))

(defn- pointer-address [x]
  (.address (dt-ffi/->pointer x)))

(def ^:private classes
  (delay
    {:NSString (objc [NSString class])
     :NSURL (objc [NSURL class])
     :NSNumber (objc [NSNumber class])
     :NSBoolean (objc [[NSNumber :numberWithBool ~(byte 1)] :class])
     :NSArray (objc [NSArray class])
     :NSDictionary (objc [NSDictionary class])
     :NSError (objc [NSError class])
     :NSNull (objc [NSNull class])}))

(defn- kind-of? [o class-key]
  (not (zero? (long (objc ^byte [o :isKindOfClass ~(get @classes class-key)])))))

(defn- nsboolean? [n]
  (= (pointer-address (get @classes :NSBoolean))
     (pointer-address (objc [n :class]))))

(defn NSNumber->clj [n]
  (when-not (nil-pointer? n)
    (if (nsboolean? n)
      (not (zero? (long (objc ^byte [n :boolValue]))))
      (case (dt-ffi/c->string (objc [n :objCType]))
        ("f" "d") (objc ^double [n :doubleValue])
        ("c" "C" "s" "S" "i" "I" "l" "L" "q" "Q")
        (objc ^long [n :longLongValue])
        (objc ^double [n :doubleValue])))))

(defn NSNull->nil [_]
  nil)

(declare objc->clj)

(defn NSArray->vec [array]
  (when-not (nil-pointer? array)
    (mapv (fn [i]
            (objc->clj (objc [array :objectAtIndex i])))
          (range (objc ^long [array :count])))))

(defn NSDictionary->map [dict]
  (when-not (nil-pointer? dict)
    (let [keys (objc [dict :allKeys])]
      (into {}
            (map (fn [i]
                   (let [key (objc [keys :objectAtIndex i])]
                     [(str (objc->clj key))
                      (objc->clj (objc [dict :objectForKey key]))])))
            (range (objc ^long [keys :count]))))))

(defn NSError->map [error]
  (when-not (nil-pointer? error)
    {:type :NSError
     :domain (NSString->str (objc [error :domain]))
     :code (objc ^long [error :code])
     :localized-description (NSString->str (objc [error :localizedDescription]))
     :localized-failure-reason (NSString->str (objc [error :localizedFailureReason]))
     :localized-recovery-suggestion (NSString->str (objc [error :localizedRecoverySuggestion]))
     :user-info (NSDictionary->map (objc [error :userInfo]))}))

(defn objc->clj [o]
  (cond
    (nil-pointer? o) nil
    (kind-of? o :NSNull) (NSNull->nil o)
    (kind-of? o :NSString) (NSString->str o)
    (kind-of? o :NSURL) (NSURL->str o)
    (kind-of? o :NSNumber) (NSNumber->clj o)
    (kind-of? o :NSArray) (NSArray->vec o)
    (kind-of? o :NSDictionary) (NSDictionary->map o)
    (kind-of? o :NSError) (NSError->map o)
    :else (objc/arc! o)))
