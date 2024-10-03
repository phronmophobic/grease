(ns app
  (:require [membrane.basic-components :as basic]
            [com.phronemophobic.clj-libffi :as ffi]
            [com.phronemophobic.clj-libffi.callback :as cb]
            [membrane.components.code-editor.code-editor :as code-editor]
            [liq.buffer :as buffer]
            [membrane.component
             :refer [defui defeffect]]
            [babashka.fs :as fs]
            [clojure.java.io :as io]
            [tech.v3.datatype.ffi :as dt-ffi ]
            [membrane.ui :as ui]
            [clojure.data.xml :as xml]
            [clojure.string :as str]
            babashka.nrepl.server
            [com.phronemophobic.grease.ios :as ios]
            [com.phronemophobic.grease.component :as gcomp]
            [com.phronemophobic.objcjure :refer [objc describe]
             :as objc])
  (:import java.io.ByteArrayInputStream
           java.io.PushbackReader))

(def insets
  (ios/safe-area-insets))
(def screen-size (ios/screen-size))
(def min-pad 0)
(def left-margin (max min-pad (:left insets)))
(def right-margin (max min-pad (:right insets)))
(def top-margin (max min-pad (:top insets)))
(def bottom-margin (max min-pad (:bottom insets)))
(def main-width
  (max 0
       (- (:width screen-size)
          left-margin
          right-margin)))
(def main-height
  (max 0
       (- (:height screen-size)
          top-margin
          bottom-margin)))
(def scroll-button-size 7)

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
           fileSystemRepresentation])))) 

(def scripts-dir
  (doto (fs/file  (documents-dir)
                  "scripts")
    fs/create-dirs))

(defn oprn [o]
  (println
   (objc/nsstring->str
    (objc
     [o :description]))))

(defn log-exception [e]
  (with-open [w (io/writer (fs/file scripts-dir
                                    "exception.log"))]
    (let [exception (objc/nsstring->str
                     (objc [e :description]))
          symbols (when-let [symbols (objc [e :callStackSymbols])]
                    (objc/nsstring->str
                     (objc [symbols :description])))]
      (.write w (str/join
                 "\n"
                 ["Exception:"
                  exception
                  symbols])))))

(defonce __init-exception-logger
  (ffi/call "NSSetUncaughtExceptionHandler"
            :void
            :pointer
            (cb/make-callback log-exception
                              :void [:pointer])))


(defn file-row [f]
  (let [view (ui/bordered
              20
              (ui/label (fs/file-name f)))
        view (ui/on
              :mouse-down
              (fn [_]
                [[::select-file {:file f}]])
              view)]
    view))

(defeffect ::open-file [{:keys [$buffers file $selected-file]}]
  (dispatch! :update $buffers
             (fn [buffers]
               (if (contains? buffers file)
                 buffers
                 (assoc buffers file
                        (-> (buffer/buffer
                             (clojure.string/join
                              "\n"
                              (fs/read-all-lines file)))
                            (code-editor/highlight))))))

  (dispatch! :set $selected-file file))

(defeffect ::select-file [{:keys [file $dir] :as m}]
  (if (fs/directory? file)
    (dispatch! :set $dir file)
    ;; else
    (dispatch! ::open-file m)))


(defonce log* (atom []))
(defn log [& args]
  (swap! log* (fn [log]
                (into log args)))
  nil)

(declare repaint!)
(def eval-ns *ns*)
(defeffect ::eval-buf [{:keys [buf]}]
  (future
    (try
      (let [s (buffer/text buf)
            is (ByteArrayInputStream. (.getBytes s "utf-8"))
            eof (Object.)]
        (with-open [is is
                    rdr (io/reader is)
                    rdr (PushbackReader. rdr)]
          (binding [*ns* eval-ns]
            (try
              (loop []
                (let [form (read rdr false eof)]
                  (when (not= eof form)
                    (eval form)
                    (recur))))
              (when-let [-main (resolve '-main)]
                ((deref -main)))
              (catch Exception e
                (log e))))))
      (catch Exception e
        (log e)))
    (repaint!)))

(defeffect ::save-file [{:keys [file buf]}]
  (let [s (buffer/text buf)]
    (fs/write-bytes file (.getBytes s "utf-8"))))

(defeffect ::delete-file [{:keys [file buf $file $last-updated]}]
  (future
    (when (ios/prompt-bool {:title "Delete?"
                            :message (str file)})
      (fs/delete-if-exists file)
      (dispatch! :set $last-updated (java.util.Date.))
      (dispatch! :set $file nil))))

(defui file-editor [{:keys [file buf last-updated]}]
  (let [buttons
        (ui/horizontal-layout
         (ui/spacer 30 0)
         (basic/button {:text "<"
                        :on-click (fn []
                                    [[:set $file nil]])})
         (basic/button {:text "eval"
                        :on-click
                        (fn []
                          [[::eval-buf {:buf buf}]])})
         (basic/button {:text "save"
                        :on-click
                        (fn []
                          [[::save-file {:file file
                                         :buf buf}]])})
         (basic/button {:text "delete"
                        :on-click
                        (fn []
                          [[::delete-file {:file file
                                           :$file $file
                                           :$last-updated $last-updated}]])}))
        scrollview-width (- main-width scroll-button-size)
        scrollview-height (- main-height
                             (ui/height buttons))]
    (ui/vertical-layout
     buttons
     (gcomp/scrollview
      {:scroll-bounds [scrollview-width scrollview-height]
       :$body nil
       :body (code-editor/text-editor {:buf buf})}))))

(defn prompt-create-file []
  (let [p (promise)]
    (ios/prompt-for-text {:title "New File"
                          :message "Choose a file name"
                          :ok-text "Create"
                          :on-ok
                          (fn [s]
                            (deliver p (objc/nsstring->str s)))
                          :on-cancel
                          (fn []
                            (deliver p nil))})
    @p))

(defeffect ::create-file [{:keys [dir $last-update]}]
  (future
    (when-let [file-name (prompt-create-file)]
      (fs/create-file (fs/file dir
                               file-name))
      (dispatch! :set $last-update (java.util.Date.)))))

(defui dir-viewer [{:keys [dir nrepl-server]}]
  (let [last-update (get extra ::last-update)]
    (ui/vertical-layout
     (if nrepl-server
       (ui/horizontal-layout
        (basic/button {:text "stop nrepl"
                       :on-click
                       (fn []
                         [[::stop-nrepl-server]])})
        (ui/label (str (-> nrepl-server
                           :socket
                           .getInetAddress
                           .getHostAddress)
                       ":"
                       (-> nrepl-server
                           :socket
                           .getLocalPort))))
       (basic/button {:text "start nrepl"
                      :on-click
                      (fn []
                        [[::start-nrepl-server]])}))
     (basic/button {:text "new"
                      :on-click
                      (fn []
                        [[::create-file {:dir dir
                                         :$last-update $last-update}]])})
     (let [relative-path (str/join 
                          " / "
                          (cons
                           "."
                           (reverse
                            (eduction
                             (take-while some?)
                             (take-while #(not (fs/same-file? %
                                                              scripts-dir)))
                             (map fs/file-name)

                             (iterate fs/parent dir)))))]
       (ui/label (str "dir: " relative-path)))
     (gcomp/scrollview
      {:scroll-bounds [250 500]
       :extra (get extra [::scroll dir])
       :$body nil
       :body(let [files (sort-by
                         (fn [f]
                           (str/lower-case (fs/file-name f)))
                         (fs/list-dir dir))]
              (apply
               ui/vertical-layout
               (when-not (fs/same-file? dir scripts-dir)
                 (ui/on
                  :mouse-down
                  (fn [_]
                    [[::select-file {:file (fs/parent dir)}]])
                  (ui/bordered
                   20
                   (ui/label ".."))))
               (for [f files]
                 (file-row f))))}))))

(defui file-viewer [{:keys [dir selected-file buffers nrepl-server]}]
  (ui/translate
   left-margin top-margin
   (ui/on
    ::select-file
    (fn [m]
      [[::select-file
        (assoc m
               :$buffers $buffers
               :$dir $dir
               :$selected-file $selected-file)]])
    (ui/vertical-layout
     (let [last-updated (get extra ::last-updated)]
       (if selected-file
         (file-editor {:file selected-file
                       :last-updated last-updated
                       :buf (get buffers selected-file)})
         (dir-viewer {:dir dir
                      :last-update last-updated
                      :nrepl-server nrepl-server})))))))

(defn delete-X []
  (ui/with-style :membrane.ui/style-stroke
    (ui/with-color
      [1 0 0]
      (ui/with-stroke-width
        3
        [(ui/path [0 0]
                  [10 10])
         (ui/path [10 0]
                  [0 10])]))))

(defeffect ::close-app [{:keys [$app]}]
  (dispatch! :set $app nil))

(defui app-view [{:keys [app]}]
  [(try
     (ui/try-draw
      (:view app)
      (fn [draw e]
        (log e)
        (draw (ui/label "Error!"))))
     (catch Exception e
       (log e)
       (ui/label "Error!")))
   (let [close-button (ui/bordered
                       10
                       (delete-X))
         button-x (- (:width screen-size)
                     right-margin
                     (ui/width close-button)
                     5)
         button-y top-margin]
     (ui/translate button-x
                   button-y
                   (ui/on
                    :mouse-down
                    (fn [_]
                      [[::close-app {:$app $app}]])
                    close-button)))])

(defui main-app-view [{:keys [dir selected-file buffers nrepl-server app]}]
  (if app
    (app-view {:app app})
    (file-viewer {:dir dir
                  :selected-file selected-file
                  :buffers buffers
                  :nrepl-server nrepl-server})))

(defn initial-state []
  {:dir scripts-dir})

(defonce app-state (atom (initial-state)))
(defn init! []
  (reset! app-state (initial-state)))

(def app (membrane.component/make-app #'main-app-view app-state))

(defn repaint! []
  (reset! ios/main-view (app))
  nil)

(defonce __initial_paint
  (repaint!))

(add-watch app-state ::update-view
           (fn [k ref old updated]
             (repaint!)))

(add-watch app-state ::handle-keyboard
           (fn [k ref old new]
             (let [focus-path [:membrane.component/context :focus]
                   old-focus (get-in old focus-path)
                   new-focus (get-in new focus-path)]
               (when (not= old-focus new-focus)
                 (future
                   (if new-focus
                     (ios/show-keyboard)
                     (ios/hide-keyboard)))))))

(defeffect ::stop-nrepl-server []
  (let [[{:keys [nrepl-server]} _] (swap-vals! app-state dissoc :nrepl-server)]
    (when nrepl-server
      (babashka.nrepl.server/stop-server! nrepl-server)))
  nil)

(defeffect ::start-nrepl-server []
  (dispatch! ::stop-nrepl-server)
  (let [host (.getHostAddress (ios/get-local-address))
        port 22345
        sci-ctx (ios/get-sci-ctx)
        server (babashka.nrepl.server/start-server!
                sci-ctx
                {:host host :port port
                 :xform ios/server-xform})]
    (swap! app-state assoc :nrepl-server server))

  nil)


(add-watch app-state
           ::close-app
           (fn [k ref old new]
             (when (not= (-> old :app :view-fn)
                         (-> new :app :view-fn))
               (when-let [on-close (-> old :app :on-close)]
                 (try
                   (on-close)
                   (catch Exception e
                     (log e)))))))

(defn show! [{:keys [view-fn
                     on-close]}]
  (swap! app-state assoc :app {:view-fn view-fn
                               :on-close on-close})
  (let [repaint! (fn []
                   (let [old @app-state
                         new (update old :app
                                     (fn [app]
                                       (when-let [view-fn (:view-fn app)]
                                         (assoc app :view
                                                (try
                                                  (view-fn)
                                                  (catch Exception e
                                                    (log e)
                                                    (ui/label "Error")))))))]
                     ;; only try once
                     (compare-and-set! app-state old new)))]
    {:repaint! repaint!}))



