(ns accelerometer
  (:require [membrane.ui :as ui]
            [membrane.component
             :refer [defui defeffect]]
            app
            [tech.v3.datatype.struct :as dt-struct]
            [com.phronemophobic.objcjure :refer [objc describe]
             :as objc]))

(defonce log* (atom []))
(defn log [& msgs]
  (swap! log* into msgs)
  nil)


(defn push [xs x n]
  (let [accelerations (conj xs x)]
    ;; should be using a queue
    (subvec accelerations (max 0 (- (count accelerations)
                                    n)))))

(def motion (objc [[CMMotionManager :alloc] :init]))

(objc ^void [motion :setAccelerometerUpdateInterval 0.05])

(defonce accelerations (atom []))

(def main-queue (objc [NSOperationQueue :mainQueue]))

(dt-struct/define-datatype!
  :cm_accleration
  [{:name :x :datatype :float64}
   {:name :y :datatype :float64}
   {:name :z :datatype :float64}])



(def scale 100)

(defn abs [x]
  (if (pos? x)
    x
    (- x)))


(defn button [text on-click]
  (ui/on
   :mouse-down
   (fn [_]
     (when on-click
       (on-click)))
   (ui/bordered
    [20 20]
    (ui/label text (ui/font nil 33)))))

(defonce app-state (atom {:accelerations []}))

(defn show-accelerations [accs]
  (ui/vertical-layout
   (ui/translate
    10 100
    (ui/horizontal-layout
     (button
      "start"
      (fn []
        (objc ^void
         [motion :startAccelerometerUpdatesToQueue:withHandler
          main-queue
          (fn ^void [data error]
            (let [acceleration (objc ^cm_accleration [data :acceleration])]
              (swap! app-state update :accelerations
                     push acceleration 100)))])
        nil))
     (ui/spacer 50 0)
     (button
      "stop"
      (fn []
        (objc ^void [motion :stopAccelerometerUpdates])
        nil))))
   (apply
    ui/vertical-layout
    (for [{:keys [x y z]} (take-last 90 accs)]
      (let [body (apply
                  ui/horizontal-layout
                  (for [[a color] [[x [1 0 0]]
                                   [y [0 1 0]]
                                   [z [0 0 1]]]]
                    (ui/filled-rectangle color
                                         (* (abs a) scale) 5)))
            [w h] (ui/bounds body)]
        body)))))



(defn viewfn []
  (show-accelerations (:accelerations @app-state)))



(defn repaint! []
  (when-let [repaint! (:repaint! @app-state)]
    (repaint!)))

(defn -main []
  (let [{:keys [repaint!]}
        (app/show! {:on-close (fn []
                                (swap! app-state dissoc :repaint!))
                    :view-fn viewfn})]
    (swap! app-state assoc
           :repaint! repaint!
           :accelerations [])
    (repaint!)))

(add-watch app-state ::update-view
           (fn [_ _ _ _]
             (repaint!)))




