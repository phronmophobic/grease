(ns accelerometer
  (:require [membrane.ui :as ui]
            [tech.v3.datatype.struct :as dt-struct]
            [com.phronemophobic.objcjure :refer [objc describe]
             :as objc]))

(def motion (objc [[CMMotionManager :alloc] :init]))

(objc ^void [motion :setAccelerometerUpdateInterval 0.05])

(def accelerations (atom []))

(def main-queue (objc [NSOperationQueue :mainQueue]))

(dt-struct/define-datatype!
  :cm_accleration
  [{:name :x :datatype :float64}
   {:name :y :datatype :float64}
   {:name :z :datatype :float64}])

(objc ^void
      [motion :startAccelerometerUpdatesToQueue:withHandler
       main-queue
       (fn ^void [data error]
         (let [acceleration (objc ^cm_accleration [data :acceleration])]
           (swap! accelerations conj acceleration)))])

(def scale 100)

(defn abs [x]
  (if (pos? x)
    x
    (- x)))

(defn show-accelerations [accelerations]
  (apply
   ui/vertical-layout
   (for [{:keys [x y z]} (take-last 90 accelerations)]
     (let [body (apply
                 ui/horizontal-layout
                 (for [[a color] [[x [1 0 0]]
                                  [y [0 1 0]]
                                  [z [0 0 1]]]]
                   (ui/filled-rectangle color
                                        (* (abs a) scale) 5)))
           [w h] (ui/bounds body)]
       body))))

(add-watch accelerations ::update-view
           (fn [_ _ old accelerations]
             (reset! main-view
                     (ui/translate 25 100
                                   (show-accelerations accelerations)))))

(comment
  (objc
   [motion :stopAccelerometerUpdates])
  ,)
