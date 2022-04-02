(ns gol)

(require '[membrane.ui :as ui])
(require '[com.phronemophobic.grease.membrane :refer
           [main-view]])


(def state-atm
  (atom
   {:board #{[1 0] [1 1] [1 2]}}))

(defn neighbours [[x y]]
  (for [dx [-1 0 1] dy (if (zero? dx) [-1 1] [-1 0 1])] 
    [(+ dx x) (+ dy y)]))

(defn step [cells]
  (set (for [[loc n] (frequencies (mapcat neighbours cells))
             :when (or (= n 3) (and (= n 2) (cells loc)))]
         loc)))

(def grid-size 20)

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

(do
  (defn view [{:keys [board running?] :as state}]
    (ui/translate
     30 30
     (ui/on
      :mouse-move
      (fn [[x y]]
        (swap! state-atm update :board
               conj [(int (/ x grid-size))
                     (int (/ y grid-size))])
        nil)
      (ui/wrap-on
       :mouse-down
       (fn [handler [x y]]
         
         (swap! state-atm update :board
                conj [(int (/ x grid-size))
                      (int (/ y grid-size))])
         (handler [x y]))
       [(ui/spacer 600 600)
        
        (into []
                (map (fn [[x y]]
                       (ui/translate (* x grid-size) (* y grid-size)
                                     (ui/rectangle grid-size grid-size))))
                board)
        (ui/translate 0 400
                      (ui/on
                       :mouse-down
                       (fn [_]
                         (swap! state-atm
                                update :running? not)
                         nil)
                       (big-button (if running?
                                     "Stop"
                                     "Start")))
                      )])))
    )
  (reset! main-view (view @state-atm)))

(defn add-random []
  (swap! state-atm
         update :board
         (fn [board]
           (into board
                 (repeatedly 30 (fn []
                                  [(rand-int 20)
                                   (rand-int 30)])))))
  )

(add-watch state-atm ::update-view (fn [k ref old updated]
                                     (reset! main-view (view updated))))

(add-watch state-atm ::run-gol (fn [k ref old updated]
                                 (when (and (:running? updated)
                                            (not (:running? old)))
                                   (future
                                     (while (:running? @state-atm)
                                       (swap! state-atm
                                              update :board step)
                                       (sleep 30))))))

(add-random)
(swap! state-atm identity)

(defn run-gol [nsteps]
  (dotimes [i nsteps]
    (swap! state-atm
           update :board step)
    (sleep 30)))
