(ns steps
  (:require [membrane.basic-components :as basic]
            [org.httpkit.client :as http]
            [membrane.component
             :refer [defui defeffect]]
            [babashka.fs :as fs]
            [clojure.java.io :as io]
            [tech.v3.datatype.ffi :as dt-ffi ]
            [com.phronemophobic.clj-libffi :as ffi]
            [membrane.ui :as ui]
            [tech.v3.datatype.struct :as dt-struct]
            [tech.v3.datatype.native-buffer :as native-buffer]
            [com.phronemophobic.grease.ios :as ios]
            [com.phronemophobic.objcjure :refer [objc describe]
             :as objc]))

(defn oprn [o]
  (println
   (objc/nsstring->str 
    (objc
     [o :description]))))

(dt-struct/define-datatype!
  :cm_time
  [{:name :value :datatype :int64}
   {:name :timescale :datatype :int32}
   {:name :flags :datatype :uint32}
   {:name :epoch :datatype :int64}])

;; (defonce health-store (objc [[HKHealthStore alloc] init]))
(defn health-store []
  (objc/arc!
   (objc [[[HKHealthStore alloc] init] :autorelease])))

;; self.healthStore = [[HKHealthStore alloc] init];

(defn lookup-nsstring-symbol [s]
  (let [symbol  (ffi/dlsym (ffi/long->pointer (long -2 ))
                           (dt-ffi/string->c s))
        ;; indirect once
        ptr (ffi/long->pointer
             (native-buffer/read-long
              (native-buffer/wrap-address
               (.address symbol)
               8
               nil)))]
    (objc/nsstring->str ptr)))

(defn lookup-enum-symbol [s]
  (let [symbol (ffi/dlsym (ffi/long->pointer (long -2))
                          (dt-ffi/string->c s))]
    (native-buffer/read-long
              (native-buffer/wrap-address
               (.address symbol)
               8
               nil))
    ))

(defmacro defcon-str [nm]
  `(def ~nm (lookup-nsstring-symbol ~(str nm))))
(defmacro defcon-enum [nm]
  `(def ~nm (lookup-enum-symbol ~(str nm))))

(defcon-str  HKQuantityTypeIdentifierStepCount)
(defcon-str HKQuantityTypeIdentifierStepCount)


(def HKQueryOptionStrictStartDate 1)
(def HKQueryOptionStrictEndDate 2)
(def HKObjectQueryNoLimit 0)

(defn request-authorization []

  ;; NSSet *readTypes = [NSSet setWithObject:[HKObjectType quantityTypeForIdentifier:HKQuantityTypeIdentifierStepCount]];
   
  ;; [self.healthStore requestAuthorizationToShareTypes:nil readTypes:readTypes completion:^(BOOL success, NSError * _Nullable error) {
  ;;     if (success) {
  ;;         [self fetchLatestStepCount];
  ;;     } else {
  ;;         NSLog(@"Request Authorization Failed: %@", error);
  ;;     }
  ;; }];
  (let [read-types (objc @#{[HKObjectType :quantityTypeForIdentifier @HKQuantityTypeIdentifierStepCount]})
        p (promise)]
    (objc [~(health-store) :requestAuthorizationToShareTypes:readTypes:completion
           nil
           read-types
           (fn ^void [^byte success err]
             (println success err)
             (deliver p (not (zero? success))))])
    @p)
  )

(println "available? " (objc ^byte [HKHealthStore isHealthDataAvailable]))


;; - (void)fetchLatestStepCount {
(defn fetch-todays-steps []

  (let [

;;     HKQuantityType *stepCountType = [HKQuantityType quantityTypeForIdentifier:HKQuantityTypeIdentifierStepCount];
        step-count-type (objc [HKQuantityType :quantityTypeForIdentifier @HKQuantityTypeIdentifierStepCount])
    
;;     // Get the start of today
;;     NSCalendar *calendar = [NSCalendar currentCalendar];
        calendar (objc [NSCalendar :currentCalendar])
;;     NSDate *now = [NSDate date];
        now (objc [NSDate :date])
;;     NSDate *startOfDay = [calendar startOfDayForDate:now];
        start-of-day (objc [calendar :startOfDayForDate now])
    
;;     // Create a predicate to filter the query
;;     NSPredicate *predicate = [HKQuery predicateForSamplesWithStartDate:startOfDay endDate:now options:HKQueryOptionStrictStartDate];
        predicate (objc
                   [HKQuery :predicateForSamplesWithStartDate:endDate:options
                    start-of-day
                    now
                    HKQueryOptionStrictStartDate])

;;     // Create a sample query
;;     HKSampleQuery *sampleQuery = [[HKSampleQuery alloc] initWithSampleType:stepCountType predicate:predicate limit:HKObjectQueryNoLimit sortDescriptors:nil resultsHandler:^(HKSampleQuery *query, NSArray *results, NSError *error) {
;;         if (!error && results) {
;;             double totalSteps = 0;

;;             for (HKQuantitySample *sample in results) {
;;                 HKQuantity *quantity = sample.quantity;
;;                 HKUnit *countUnit = [HKUnit countUnit];
;;                 double stepCount = [quantity doubleValueForUnit:countUnit];
;;                 totalSteps += stepCount;
;;             }
            
;;             dispatch_async(dispatch_get_main_queue(), ^{
;;                 NSLog(@"Total steps for today: %.0f", totalSteps);
;;             });
;;         } else {
;;             NSLog(@"Fetch Steps Failed: %@", error);
;;         }
;;     }];
        resultsp (promise)
        sample-query (objc [[HKSampleQuery :alloc]
                            :initWithSampleType:predicate:limit:sortDescriptors:resultsHandler
                            step-count-type
                            predicate
                            HKObjectQueryNoLimit
                            nil
                            (fn ^void [query results error]
                              (when (and (zero? (.address error))
                                         (not (zero? (.address results))))
                                (let [total-steps
                                      (reduce
                                       (fn [steps i]
                                         (let [sample (objc [results :objectAtIndex i])
                                               quantity (objc [sample :valueForKey @"quantity"])
                                               count-unit (objc [HKUnit :countUnit])
                                               step-count (objc ^double [quantity :doubleValueForUnit count-unit])]
                                           (+ steps step-count)))
                                       0
                                       (range (objc ^long [results :count])))]
                                  (deliver resultsp total-steps))))])
        
    
;;     [self.healthStore executeQuery:sampleQuery];
;; }
        

]
    (objc [~(health-store) :executeQuery sample-query])
    @resultsp)
  )

   
(defeffect ::update-steps [{:keys [$steps]}]

  (dispatch! :set $steps "...")
  (future
    (dispatch! :set $steps (fetch-todays-steps))))

(defui steps-ui [{:keys [steps debug]}]
  (ui/translate
   30 80
   (ui/vertical-layout
    (ui/label (if (double? steps)
                (format "%,d" (long steps))
                steps)
              (ui/font nil 50))
    (ui/label debug)
    (ui/spacer 10)
    (ui/translate
     80 300
     (basic/button {:text "refresh"
                    :on-click
                    (fn []
                      [[::update-steps {:$steps $steps}]])})))))

(defonce app-state (atom {:steps nil}))
(def app (membrane.component/make-app #'steps-ui app-state))

(defn observer-query []
  (let [
    ;; HKQuantityType *stepCountType = [HKQuantityType quantityTypeForIdentifier:HKQuantityTypeIdentifierStepCount];
        step-count-type (objc [HKQuantityType :quantityTypeForIdentifier @HKQuantityTypeIdentifierStepCount])
    
    ;; // Set up an observer query
        observer-query (objc [[HKObserverQuery :alloc]
                              :initWithSampleType:predicate:updateHandler
                              step-count-type
                              nil
                              (fn ^void [query handler error]
                                (swap! app-state assoc :debug
                                       (str (objc/nsstring->str (objc [[NSDate :date] :description]))
                                            "\n"
                                            error))
                                (objc/invoke-block handler :void)
                                #_(when handler
                                    (ffi/call-ptr handler :void)))])
    ;; HKObserverQuery *observerQuery = [[HKObserverQuery alloc] initWithSampleType:stepCountType predicate:nil updateHandler:^(HKObserverQuery *query, HKObserverQueryCompletionHandler completionHandler, NSError *error) {
    ;;     if (!error) {
    ;;         [self fetchLatestStepCount];
    ;;     }
    ;;     completionHandler();
    ;; }];

        ;; [self.healthStore executeQuery:observerQuery];
        ]
    (objc ^void [~(health-store) :executeQuery observer-query])))

(defn -main []
  #_(observer-query)
  (let [{:keys [repaint!]}
        (app/show! {:on-close (fn []
                                (swap! app-state dissoc :repaint!))
                    :view-fn app})]
    (swap! app-state assoc :repaint! repaint!)))

(add-watch app-state ::update-view
           (fn [k ref old updated]
             (when-let [repaint! (:repaint! updated)]
               (when (not= old updated)
                 (repaint!)))))



