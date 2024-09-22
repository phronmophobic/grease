(ns app
  (:require [membrane.basic-components :as basic]
            [com.phronemophobic.clj-libffi :as ffi]
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
            [com.phronemophobic.grease.component :as gui]
            [com.phronemophobic.objcjure :refer [objc describe]
             :as objc]))

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
    (let [s (buffer/text buf)]
      (binding [*ns* eval-ns]
        (load-string s)))
    (repaint!)))

(defui file-editor [{:keys [file buf]}]
  (ui/translate
   0 30
   (ui/vertical-layout
    (ui/horizontal-layout
     (basic/button {:text "<"
                    :on-click (fn []
                                [[:set $file nil]])})
     (basic/button {:text "eval"
                    :on-click
                    (fn []
                      [[::eval-buf {:buf buf}]])}))
    (gui/scrollview
     {:scroll-bounds [250 350]
      :$body nil
      :body (code-editor/text-editor {:buf buf})}))))

(defui dir-viewer [{:keys [dir nrepl-server]}]
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
   (gui/scrollview
    {:scroll-bounds [250 500]
     :extra (get extra [::scroll dir])
     :$body nil
     :body(let [files (fs/list-dir dir)]
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
               (file-row f))))})))

(defui file-viewer [{:keys [dir selected-file buffers nrepl-server]}]
  (ui/translate
   30 50
   (ui/on
    ::select-file
    (fn [m]
      [[::select-file
        (assoc m
               :$buffers $buffers
               :$dir $dir
               :$selected-file $selected-file)]])
    (ui/vertical-layout
     (if selected-file
       (file-editor {:file selected-file
                     :buf (get buffers selected-file)})
       (dir-viewer {:dir dir
                    :nrepl-server nrepl-server}))))))

(defn initial-state []
  {:dir scripts-dir})

(defonce app-state (atom (initial-state)))
(defn init! []
  (reset! app-state (initial-state)))

(def app (membrane.component/make-app #'file-viewer app-state))

(defn repaint! []
  (reset! ios/main-view (app)))

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
        sci-ctx (ios/new-sci-ctx)
        server (babashka.nrepl.server/start-server!
                sci-ctx
                {:host host :port port
                 :xform ios/server-xform})]
    (swap! app-state assoc :nrepl-server server))

  nil)






