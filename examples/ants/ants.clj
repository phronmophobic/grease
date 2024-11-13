;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; ant sim ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Common Public License 1.0 (http://opensource.org/licenses/cpl.php)
;   which can be found in the file CPL.TXT at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ants
  (:require [com.phronemophobic.grease.ios :as ios]
            [membrane.ui :as ui]))



;dimensions of square world
(def dim 80)
;number of ants = nants-sqrt^2
(def nants-sqrt 7)
;number of places with food
(def food-places 35)
;range of amount of food at a place
(def food-range 100)
;scale factor for pheromone drawing
(def pher-scale 20.0)
;scale factor for food drawing
(def food-scale 30.0)
;evaporation rate
(def evap-rate 0.99)

(def animation-sleep-ms 100)
(def ant-sleep-ms 40)
(def evap-sleep-ms 1000)

(defrecord Cell [food pher]) ;may also have :ant and :home

;world is a 2d vector of refs to cells
(def world 
     (apply vector 
            (map (fn [_] 
                   (apply vector (map (fn [_] (volatile! (->Cell 0 0))) 
                                      (range dim)))) 
                 (range dim))))

(defn place [[x y]]
  (-> world (nth x) (nth y)))

(defrecord Ant [dir]) ;may also have :food

(defn create-ant 
  "create an ant at the location, returning an ant agent on the location"
  [loc dir]
    (let [p (place loc)
          a (->Ant dir)]
      (vswap! p assoc :ant a)
      (volatile! loc)))

(def home-off (/ dim 4))
(def home-range (range home-off (+ nants-sqrt home-off)))

(defn setup 
  "places initial food and ants, returns seq of ant agents"
  []
  (dotimes [i food-places]
    (let [p (place [(rand-int dim) (rand-int dim)])]
      (vswap! p assoc :food (rand-int food-range))))
  (doall
   (for [x home-range y home-range]
     (do
       (vswap! (place [x y]) 
               assoc :home true)
       (create-ant [x y] (rand-int 8))))))

(defn bound 
  "returns n wrapped into range 0-b"
  [b n]
  (let [n (rem n b)]
    (if (neg? n) 
      (+ n b) 
      n)))

(defn wrand 
  "given a vector of slice sizes, returns the index of a slice given a
  random spin of a roulette wheel with compartments proportional to
  slices."
  [slices]
  (let [total (reduce + slices)
        r (rand total)]
    (loop [i 0 sum 0]
      (if (< r (+ (slices i) sum))
        i
        (recur (inc i) (+ (slices i) sum))))))

;dirs are 0-7, starting at north and going clockwise
;these are the deltas in order to move one step in given dir
(def dir-delta {0 [0 -1]
                1 [1 -1]
                2 [1 0]
                3 [1 1]
                4 [0 1]
                5 [-1 1]
                6 [-1 0]
                7 [-1 -1]})

(defn delta-loc 
  "returns the location one step in the given dir. Note the world is a torus"
  [[x y] dir]
  (let [[dx dy] (dir-delta (bound 8 dir))]
    [(bound dim (+ x dx)) (bound dim (+ y dy))]))

;(defmacro dosync [& body]
;  `(sync nil ~@body))

;ant agent functions
;an ant agent tracks the location of an ant, and controls the behavior of 
;the ant at that location

(defn turn 
  "turns the ant at the location by the given amount"
  [loc amt]
  (let [p (place loc)
        ant (:ant @p)]
    (vswap! p assoc :ant (assoc ant :dir (bound 8 (+ (:dir ant) amt)))))
    loc)

(defn move 
  "moves the ant in the direction it is heading. Must be called in a
  transaction that has verified the way is clear"
  [loc]
  (let [oldp (place loc)
        ant (:ant @oldp)
        newloc (delta-loc loc (:dir ant))
        p (place newloc)]
                                        ;move the ant
    (vswap! p assoc :ant ant)
    (vswap! oldp dissoc :ant)
                                        ;leave pheromone trail
    (when-not (:home @oldp)
      (vswap! oldp assoc :pher (inc (:pher @oldp))))
    newloc))

(defn take-food [loc]
  "Takes one food from current location. Must be called in a
  transaction that has verified there is food available"
  (let [p (place loc)
        ant (:ant @p)]    
    (vswap! p assoc 
           :food (dec (:food @p))
           :ant (assoc ant :food true))
    loc))

(defn drop-food [loc]
  "Drops food at current location. Must be called in a
  transaction that has verified the ant has food"
  (let [p (place loc)
        ant (:ant @p)]    
    (vswap! p assoc 
           :food (inc (:food @p))
           :ant (dissoc ant :food))
    loc))

(defn rank-by 
  "returns a map of xs to their 1-based rank when sorted by keyfn"
  [keyfn xs]
  (let [sorted (sort-by (comp float keyfn) xs)]
    (reduce (fn [ret i] (assoc ret (nth sorted i) (inc i)))
            {} (range (count sorted)))))

(defn behave 
  "the main function for the ant agent"
  [loc]
  (let [p (place loc)
        ant (:ant @p)
        ahead (place (delta-loc loc (:dir ant)))
        ahead-left (place (delta-loc loc (dec (:dir ant))))
        ahead-right (place (delta-loc loc (inc (:dir ant))))
        places [ahead ahead-left ahead-right]]
    (if (:food ant)
                                        ;going home
      (cond 
        (:home @p)                              
        (-> loc drop-food (turn 4))
        (and (:home @ahead) (not (:ant @ahead))) 
        (move loc)
        :else
        (let [ranks (merge-with + 
                                (rank-by (comp #(if (:home %) 1 0) deref) places)
                                (rank-by (comp :pher deref) places))]
          (([move #(turn % -1) #(turn % 1)]
            (wrand [(if (:ant @ahead) 0 (ranks ahead)) 
                    (ranks ahead-left) (ranks ahead-right)]))
           loc)))
                                        ;foraging
      (cond 
        (and (pos? (:food @p)) (not (:home @p))) 
        (-> loc take-food (turn 4))
        (and (pos? (:food @ahead)) (not (:home @ahead)) (not (:ant @ahead)))
        (move loc)
        :else
        (let [ranks (merge-with + 
                                (rank-by (comp :food deref) places)
                                (rank-by (comp :pher deref) places))]
          (([move #(turn % -1) #(turn % 1)]
            (wrand [(if (:ant @ahead) 0 (ranks ahead)) 
                    (ranks ahead-left) (ranks ahead-right)]))
           loc))))))

(defn evaporate 
  "causes all the pheromones to evaporate a bit"
  []
  (dorun 
   (for [x (range dim) y (range dim)]
     (let [p (place [x y])]
       (vswap! p assoc :pher (* evap-rate (:pher @p)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; UI ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;pixels per world cell
(def scale 3.99)


(defn render-ant [ant x y]
  (let [black [0 0 0]
        gray [0.4 0.4 0.4]
        red [1 0 0]
        [hx hy tx ty] ({0 [2 0 2 4] 
                        1 [4 0 0 4] 
                        2 [4 2 0 2] 
                        3 [4 4 0 0] 
                        4 [2 4 2 0] 
                        5 [0 4 4 0] 
                        6 [0 2 4 2] 
                        7 [0 0 4 4]}
                       (:dir ant))]
    (ui/with-style :membrane.ui/style-stroke
     (ui/with-color (if (:food ant) 
                      [1 0 0]
                      [0 0 0])
       (ui/path
        [(+ hx (* x scale)) (+ hy (* y scale))] 
        [(+ tx (* x scale)) (+ ty (* y scale))])))))

(defn render-place [p x y]
  (into []
        (remove nil?)
        [(when (pos? (:pher p))
             (ui/translate (* x scale) (* y scale)
                           (ui/with-color [0 1 0 (/ (:pher p) pher-scale)]
                             (ui/rectangle scale scale))))
         
         (when (pos? (:food p))
           (ui/translate (* x scale) (* y scale)
                         (ui/with-color [1 0 0 (/ (:food p) food-scale)]
                          (ui/rectangle scale scale))))
         (when (:ant p)
           (render-ant (:ant p) x y))])
  )

(defonce state-atm
  (atom {}))



(defn big-button [text]
  (let [lbl (ui/label text
                      (ui/font nil 42))
        body (ui/padding 0 4 14 6
                         lbl)
        [w h] (ui/bounds body)]
    [(ui/with-style
       :membrane.ui/style-stroke
       (ui/rounded-rectangle (+ w 6) h 8))
     (ui/with-color [1 1 1]
      (ui/rounded-rectangle (+ w 6) h 8))
     body]))

(declare repaint)
(defn render []
  (let [v (apply vector (for [x (range dim) y (range dim)] 
                          @(place [x y])))]

    (ui/translate
     0 30
     [(ui/with-style :membrane.ui/style-stroke
        (ui/rectangle (* dim scale)
                      (* dim scale)))
      (into []
            (comp cat)
            (for [x (range dim) y (range dim)]
              (render-place (v (+ (* x dim) y)) x y)))

      (ui/with-color [0 0 1]
        (ui/translate (* scale home-off) (* scale home-off) 
                      (ui/filled-rectangle
                       [0 0 1]
                       (* scale nants-sqrt) (* scale nants-sqrt))))

      (ui/translate 0 400
                    (ui/on
                     :mouse-down
                     (fn [_]
                       (swap! state-atm
                              update :running? not)
                       (repaint)
                       nil)
                     (big-button (if (:running? @state-atm)
                                   "Stop"
                                   "Start")))
                    )

      ])

    
    ))

(def ants (setup))


(defn repaint []
  (when-let [repaint! (:repaint! @state-atm)]
    (repaint!))
  nil)

(defn step []
  (run! #(vswap! % behave)  ants)
  (evaporate))

(defn run [n]
  (dotimes [i n]
    (step)
    (repaint)
    (ios/sleep 20)))

(add-watch state-atm
           ::run-ants
           (fn [k ref old updated]
             (when (and (:running? updated)
                        (not (:running? old)))
               (future
                 (while (:running? @state-atm)
                   (step)
                   (repaint)
                   (ios/sleep 20))))))



(defn -main []
  #_(observer-query)
  (let [{:keys [repaint!]}
        (app/show! {:on-close (fn []
                                (swap! state-atm assoc :running? false)
                                (swap! state-atm dissoc :repaint!))
                    :view-fn render})]
    (swap! state-atm assoc :repaint! repaint!)
    (swap! state-atm assoc :running? true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; use ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

