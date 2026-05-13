(ns objc
  (:require [com.phronemophobic.clj-objc :as objc]))



;; Create Objective-c class
(def NSMutableSet (objc/string->class "NSMutableSet"))

;; alloc NSMutableSet
(def my-set (objc/call-objc NSMutableSet "alloc" :pointer))
;; init NSMutableSet
(def my-set (objc/call-objc my-set "init" :pointer))

(println (objc/objc->str my-set))

(defn root-view-controller []
;; [UIApplication sharedApplication].keyWindow.rootViewController  
  (-> (objc/string->class "UIApplication")
      (objc/call-objc "sharedApplication" :pointer)
      (objc/call-objc "keyWindow" :pointer)
      (objc/call-objc "rootViewController" :pointer)))

(defn show-alert [title body ok-text]
  (objc/dispatch-main
   (fn []
     (let [           ;; UIAlertController* alert = [UIAlertController alertControllerWithTitle:@"My Alert"
           ;;                                message:@"This is an alert."
           ;;                                preferredStyle:UIAlertControllerStyleAlert];
           alert (-> (objc/string->class "UIAlertController")
                     (objc/call-objc "alertControllerWithTitle:message:preferredStyle:"
                                     :pointer
                                     :pointer (objc/->nsstring (str title))
                                     :pointer (objc/->nsstring (str body))
                                     :int32 1))


           ;; UIAlertAction* defaultAction = [UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault
           ;;    handler:^(UIAlertAction * action) {}];           
           ok-handler (objc/make-block (fn [action])
                                       :void
                                       :pointer)
           alert-action (-> (objc/string->class "UIAlertAction")
                            (objc/call-objc "actionWithTitle:style:handler:"
                                            :pointer
                                            :pointer (objc/->nsstring (str ok-text))
                                            :int32 0
                                            :pointer ok-handler))]
       ;; [alert addAction:defaultAction];

       (objc/call-objc alert "addAction:"
                       :void
                       :pointer alert-action)
       ;; [self presentViewController:alert animated:YES completion:nil];
       (objc/call-objc (root-view-controller)
                       "presentViewController:animated:completion:"
                       :void
                       :pointer alert
                       :int8 1
                       :int64 0)))))

(comment
  (show-alert "Hey!" "Check out clojure on mobile!" "Okie dokie!"))


;; Create an array 
(def arr (-> (objc/string->class "NSMutableArray")
             (objc/call-objc "array" :pointer)))

;; add objects
(do
  (objc/call-objc arr "addObject:" :void :pointer (objc/->nsstring "a"))
  (objc/call-objc arr "addObject:" :void :pointer (objc/->nsstring "bb"))
  (objc/call-objc arr "addObject:" :void :pointer (objc/->nsstring "ccc"))
  (objc/call-objc arr "addObject:" :void :pointer (objc/->nsstring "dd")))

;; helper function for finding string length
(defn str-length [nstr]
  (objc/call-objc nstr "length" :int32))

;; Make a comparator block
(def sorter
  (objc/make-block (fn [a b]
                     (let [alength (str-length a)
                           blength (str-length b)]
                       (if (> alength blength)
                         -1
                         (if (= alength blength)
                           0
                           1))))
                   :int32
                   :pointer :pointer))

;; sort array using comparator block
(def sorted-array (objc/call-objc arr "sortedArrayUsingComparator:"
                                  :pointer
                                  :pointer sorter))
(println (objc/objc->str sorted-array))

