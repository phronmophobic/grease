(ns com.phronemophobic.grease.component
  (:require [membrane.component :refer [defui
                                        defeffect]
             :as component]
            [membrane.ui :as ui
             :refer [vertical-layout
                     translate
                     horizontal-layout
                     label
                     with-color
                     with-style
                     image
                     on-click
                     on-mouse-up
                     bounds
                     spacer
                     filled-rectangle
                     rectangle
                     IBounds
                     IKeyPress
                     origin
                     origin-x
                     origin-y
                     on-key-press
                     bordered
                     children
                     maybe-key-press
                     on
                     IHandleEvent
                     index-for-position]]))

(set! *warn-on-reflection* true)

(defn ^:private clamp
  ([min-val max-val val]
   (max min-val
        (min max-val
             val)))
  ([max-val val]
   (max 0
        (min max-val
             val))))

(defn get-time []
  (.getTime ^java.util.Date (java.util.Date.)))

(def ^:private scroll-button-size 7)
(def ^:private scroll-background-color [0.941 0.941 0.941])
(def ^:private scroll-button-color [0.73 0.73 0.73])
(def ^:private scroll-button-border-color [0.89 0.89 0.89])
(def ^:private scroll-time-threshold 500)
(def ^:private scroll-distance-threshold 20)

(defn euclidean-distance [a b]
  (let [x1 (nth a 0)
        y1 (nth a 1)
        x2 (nth b 0)
        y2 (nth b 1)]
   (Math/sqrt (+ (Math/pow (- x2 x1) 2)
                 (Math/pow (- y2 y1) 2)))))

(defn vertical-scrollbar [total-height height offset-y]
  [(filled-rectangle scroll-background-color
                     scroll-button-size height)
   (let [top (/ offset-y total-height)
         bottom (/ (+ offset-y height)
                   total-height)]

     (translate 0 (* height top)
                (with-color
                  scroll-button-color
                  (ui/rounded-rectangle scroll-button-size (* height (- bottom top)) (/ scroll-button-size 2)))))
   (with-color scroll-button-border-color
     (with-style :membrane.ui/style-stroke
       (rectangle scroll-button-size height)))])


(defn horizontal-scrollbar [total-width width offset-x]
  [(filled-rectangle scroll-background-color
                     width scroll-button-size)
   (let [left (/ offset-x total-width)
         right (/ (+ offset-x width)
                  total-width)]
     (translate (* width left) 0
                (with-color
                  scroll-button-color
                  (ui/rounded-rectangle (* width (- right left)) scroll-button-size  (/ scroll-button-size 2)))))
   (with-color scroll-button-border-color
     (with-style :membrane.ui/style-stroke
       (rectangle width scroll-button-size)))])


:scroll-start-time
:scroll-dist
:scroll-mpos

(defn exceed-scroll-threshold? [scrolling
                                mpos]
  (let [{:keys [scroll-dist
                scroll-start-time
                scroll-mpos]} scrolling
        now (get-time)
        total-scroll (+ scroll-dist
                        (euclidean-distance scroll-mpos mpos))]
    (or (> (- now scroll-start-time)
           scroll-time-threshold)
        (> total-scroll scroll-distance-threshold))))

(defeffect ::scroll-pan [{:keys [$scrolling mpos $offset clampx clampy]}]
  (let [{:keys [scroll-dist
                scroll-start-mpos
                scroll-start-offset
                scroll-mpos]
         :as scrolling} (dispatch! :get $scrolling)]
   (when (exceed-scroll-threshold? scrolling mpos)
     (dispatch! :set $offset
                [(clampx (+ (nth scroll-start-offset 0)
                            (- (nth scroll-start-mpos 0)
                               (nth mpos 0)
                               )))
                 (clampy (+ (nth scroll-start-offset 1)
                            (- (nth scroll-start-mpos 1)
                               (nth mpos 1)
                               )))]))

   (dispatch! :update $scrolling
              assoc
              :scroll-dist (+ scroll-dist
                              (euclidean-distance scroll-mpos mpos))
              :scroll-mpos mpos)))

(defeffect ::cancel-scroll [{:keys [$scrolling]}]
  (dispatch! :set $scrolling nil))

(defeffect ::start-scroll [{:keys [$scrolling $offset mpos]}]
  (dispatch! :set $scrolling
             {:scroll-start-time (get-time)
              :scroll-dist 0
              :scroll-start-mpos mpos
              :scroll-start-offset (dispatch! :get $offset)
              :scroll-mpos mpos}))
(defeffect ::finish-scroll [{:keys [$scrolling]
                             :as m}]
  (dispatch! ::scroll-pan m)
  (dispatch! :set $scrolling nil))

(defui scrollview
  "Basic scrollview.

  scroll-bounds should be a two element vector of [width height] of the scrollview
  body should be an element.
"
  [{:keys [offset scroll-bounds body]
    :or {offset [0 0]}}]
  (let [offset-x (long (nth offset 0))
        offset-y (long (nth offset 1))
        [width height] scroll-bounds
        [total-width total-height] (bounds body)



        max-offset-x (max 0
                          (- total-width width))
        max-offset-y (max 0
                          (- total-height height))
        clampx (partial clamp max-offset-x)
        clampy (partial clamp max-offset-y)

        scroll-elem (ui/scrollview
                     scroll-bounds
                     ;; allow offsets to be set to values outside of bounds.
                     ;; this prevents rubber banding when resizing an element
                     ;; in a scrollview near the edges of a scroll view.
                     ;; will snap back to viewport when offset is updated.
                     [(- offset-x) #_(- (clampx offset-x))
                      (- offset-y) #_(- (clampy offset-y))]
                     (ui/->Cached body))


        body [scroll-elem
              (when (> total-height height)
                (translate width 0
                           (vertical-scrollbar total-height height offset-y)))
              (when (> total-width width)
                (translate 0 height
                           (horizontal-scrollbar total-width width offset-x)))]

        scrolling (get extra :scrolling)



        body (if scrolling
               (ui/on :mouse-move
                      (fn [mpos]
                        [[::scroll-pan {:$scrolling $scrolling
                                        :$offset $offset
                                        :clampx clampx
                                        :clampy clampy
                                        :mpos mpos}]])
                      :mouse-up
                      (fn [mpos]
                        (if (exceed-scroll-threshold? scrolling mpos)
                          [[::finish-scroll {:$scrolling $scrolling
                                             :$offset $offset
                                             :clampx clampx
                                             :clampy clampy
                                             :mpos mpos}]]

                          ;; else
                          (cons
                           [::cancel-scroll {:$scrolling $scrolling
                                             :mpos mpos}]
                           (ui/mouse-down body mpos))
                          ))
                      
                      (ui/no-events body))
               ;; else
               (ui/on :mouse-down
                      (fn [mpos]
                        [[::start-scroll {:$scrolling $scrolling
                                         :$offset $offset
                                         :mpos mpos}]])
                      body))]
    body))


(defui scroll-test [{:keys []}]
  (let [w 100
        h 500
        border-size 10
        body (ui/bordered border-size (ui/rectangle (- w border-size) (- h border-size)))

        w 500
        h 100
        body2 (ui/bordered border-size (ui/rectangle (- w border-size) (- h border-size)))
              ]
    (ui/padding 30
     (ui/bordered (scrollview {:scroll-bounds [300 300]
                               :body
                               
                               (apply
                                ui/vertical-layout
                                (for [i (range 30)]
                                  (ui/button (str "alsdjf;lasjdf-" i)
                                             )))})))))

(comment

  (require '[membrane.skia :as backend])
  (def winfo (backend/run (component/make-app #'scroll-test {} ) ))
  

  ,)


