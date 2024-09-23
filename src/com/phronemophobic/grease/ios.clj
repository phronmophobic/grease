(ns com.phronemophobic.grease.ios
  (:require membrane.ios
            [membrane.ui :as ui]
            membrane.component
            membrane.components.code-editor.code-editor
            liq.buffer
            membrane.basic-components
            [babashka.fs :as fs]
            babashka.nrepl.server
            [babashka.nrepl.server.middleware :as middleware]
            clojure.data.json
            [sci.core :as sci]
            [sci.addons :as addons]

            clojure.zip
            clojure.instant

            honey.sql
            honey.sql.protocols
            honey.sql.helpers

            [com.phronemophobic.scify :as scify]
            [com.phronemophobic.grease.ios :as ios]
            [com.phronemophobic.grease.replog :as replog]
            com.phronemophobic.grease.component
            [com.phronemophobic.objcjure :as objc
             :refer [objc]]
            com.phronemophobic.clj-libffi.callback
            [com.phronemophobic.clj-libffi :as ffi]

            [tech.v3.datatype.ffi :as dt-ffi]
            [tech.v3.datatype :as dtype]
            [tech.v3.datatype.native-buffer :as native-buffer]
            [tech.v3.datatype.struct :as dt-struct]
            [tech.v3.datatype.protocols :as dtype-proto]
            [clojure.java.io :as io]
            clojure.stacktrace)
   ;; babashka extras
  (:require babashka.impl.clojure.core.async

            ;;babashka.impl.hiccup
            babashka.impl.httpkit-client
            babashka.impl.httpkit-server
            babashka.impl.xml
            babashka.impl.jdbc
            babashka.impl.clojure.instant
            )
  (:import java.net.URL
           java.net.NetworkInterface
           java.net.InetAddress)
  (:gen-class))

(set! *warn-on-reflection* true)

(def main-view (atom nil))
(defonce debug-view (atom nil))
(defonce debug-log (atom []))

(dt-ffi/define-library-interface
  {:clj_main_view {:rettype :pointer
                   :argtypes []}

   :clj_app_dir {:rettype :pointer
                 :argtypes []}

   :clj_debug {:rettype :pointer
               :argtypes [['data :pointer]]}
   ,})


(def UIAlertControllerStyleActionSheet 0)
(def UIAlertControllerStyleAlert 1)

(def UIAlertActionStyleDefault 0)
(def UIAlertActionStyleCancel 1)
(def UIAlertActionStyleDestructive 2)

(def main-queue (delay (ffi/dlsym ffi/RTLD_DEFAULT (dt-ffi/string->c "_dispatch_main_q"))))

(defn NSSetUncaughtExceptionHandler [handler]
  (ffi/call "NSSetUncaughtExceptionHandler" :void :pointer handler)
  )

(defn dispatch-main-async [f]
  (ffi/call "dispatch_async" :void :pointer @main-queue :pointer (objc
                                                                  (fn ^void []
                                                                    (try
                                                                      (f)
                                                                      (catch Exception e
                                                                        (println e)))))))

#_(defn show-alert! []
    (dispatch-main-async
     (fn []
       (let [vc (objc [[[UIApplication :sharedApplication] :keyWindow] :rootViewController])
             alert (objc
                    [UIAlertController :alertControllerWithTitle:message:preferredStyle
                     @"Title"
                     @"Message"
                     UIAlertControllerStyleAlert])
             default-action (objc [UIAlertAction :actionWithTitle:style:handler
                                   @"default"
                                   UIAlertActionStyleDefault
                                   (fn ^void [action]
                                     (log :default-action))])]
         (objc [alert :addAction default-action])
         (objc [vc :presentViewController:animated:completion
                alert true nil])))))

(defn show-keyboard []
  (objc ^void
        [~(clj_main_view) :performSelectorOnMainThread:withObject:waitUntilDone
         ~(objc/sel_registerName (dt-ffi/string->c "becomeFirstResponder"))
         nil
         ~(byte 0)]))

(defn hide-keyboard []
  (objc ^void
        [~(clj_main_view) :performSelectorOnMainThread:withObject:waitUntilDone
         ~(objc/sel_registerName (dt-ffi/string->c "resignFirstResponder"))
         nil
         ~(byte 0)]))


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
           fileSystemRepresentation])))
  )


(defn sleep [msecs]
  (Thread/sleep (long msecs)))


(defn get-local-address []
  (let [address (->> (NetworkInterface/getNetworkInterfaces)
                     enumeration-seq
                     (filter (fn [interface]
                               (.startsWith (.getName ^NetworkInterface interface)
                                            "en")))
                     (keep
                      (fn [interface]
                        (let [ip4 (->> (.getInetAddresses ^NetworkInterface interface)
                                       enumeration-seq
                                       (some (fn [inet]
                                               (when (= 4 (count
                                                           (.getAddress ^InetAddress inet)))
                                                 inet))))]
                          ip4)))
                     (filter (fn [ip4]
                               (.isSiteLocalAddress ^InetAddress ip4)))
                     first)]
    address))


(defn get-addresses []
  (let [addresses (->> (NetworkInterface/getNetworkInterfaces)
                       enumeration-seq
                       (filter (fn [interface]
                                 (.startsWith (.getName ^NetworkInterface interface)
                                              "en")))
                       (map
                        (fn [interface]
                          (let [ip4 (->> (.getInetAddresses ^NetworkInterface interface)
                                         enumeration-seq
                                         (some (fn [inet]
                                                 (when (= 4 (count
                                                             (.getAddress ^InetAddress inet)))
                                                   inet))))]
                            (.getHostAddress ^InetAddress ip4)))))]
    addresses))


(declare sci-ctx)
(defmacro objc-wrapper [form]
  (binding [objc/*sci-ctx* @sci-ctx]
    (objc/objc-syntax &env form)))

(defn get-sci-ctx []
  @sci-ctx)

(declare show-code)
(def
  ^{::middleware/requires #{#'middleware/wrap-read-msg}
    ::middleware/expects #{#'middleware/wrap-process-message}}
  display-evals
  (map (fn [request]
         (let [msg (:msg request)]
           (case (:op msg)
             :eval (show-code (:code msg))
             :load-file (show-code (:file msg))
             nil))
         request)))

(def
  ^{::middleware/requires #{#'middleware/wrap-read-msg}
    ::middleware/expects #{#'middleware/wrap-process-message}}
  replog-evals
  (map (fn [request]
         ;; (clojure.pprint/pprint request)
         (replog/append-nrepl-op (:msg request))
         request)))

;; Add cross cutting middleware
(def server-xform
  (middleware/middleware->xform
   (conj middleware/default-middleware
         #'display-evals
         #'replog-evals)))

(def repl-requires
  '[[clojure.repl :refer [dir doc]]])

(def opts (addons/future
            {:classes
             {:allow :all
              'java.lang.System System
              'java.lang.Long Long
              'java.util.Date java.util.Date
              'java.net.URL java.net.URL
              'java.net.InetAddress java.net.InetAddress
              'java.io.ByteArrayInputStream java.io.ByteArrayInputStream
              'java.io.PushbackReader java.io.PushbackReader
              }
             :namespaces
             (merge (let [ns-name 'com.phronemophobic.grease.membrane
                          fns (sci/create-ns ns-name nil)]
                      {ns-name {'main-view (sci/copy-var main-view fns)
                                'debug-view (sci/copy-var debug-view fns)
                                'debug-log (sci/copy-var debug-log fns)
                                'server-xform (sci/copy-var server-xform fns)
                                'get-sci-ctx (sci/copy-var get-sci-ctx fns)}})

                    (do
                      (scify/scify-ns-protocol 'membrane.ui)
                      (scify/ns->ns-map 'membrane.ui))
                    (scify/ns->ns-map 'membrane.component)
                    (scify/ns->ns-map 'membrane.basic-components)
                    (scify/ns->ns-map 'membrane.components.code-editor.code-editor)
                    (scify/ns->ns-map 'liq.buffer)
                    (scify/ns->ns-map 'membrane.ios)
                    (scify/ns->ns-map 'clojure.java.io)
                    (scify/ns->ns-map 'clojure.data.json)
                    (scify/ns->ns-map 'clojure.stacktrace)
                    (scify/ns->ns-map 'com.phronemophobic.grease.ios)
                    (scify/ns->ns-map 'com.phronemophobic.grease.component)
                    (let [ns-map (scify/ns->ns-map 'com.phronemophobic.objcjure)
                          sci-ns-var (-> ns-map
                                         first
                                         val
                                         first
                                         val
                                         meta
                                         :ns)]
                      (assoc-in ns-map
                                '[com.phronemophobic.objcjure
                                  objc]
                                (sci/new-var 'objc @#'objc-wrapper
                                             (assoc (meta #'objc/objc)
                                                    :ns sci-ns-var))))
                    (scify/ns->ns-map 'tech.v3.datatype.ffi)
                    (scify/ns->ns-map 'tech.v3.datatype.native-buffer)
                    (scify/ns->ns-map 'tech.v3.datatype)
                    (scify/ns->ns-map 'tech.v3.datatype.struct)
                    (scify/ns->ns-map 'tech.v3.datatype.protocols)
                    (scify/ns->ns-map 'com.phronemophobic.clj-libffi)
                    (scify/ns->ns-map 'com.phronemophobic.clj-libffi.callback)
                    (scify/ns->ns-map 'babashka.fs)

                    (scify/ns->ns-map 'babashka.fs)
                    (scify/ns->ns-map 'babashka.nrepl.server)
                    
                    (scify/ns->ns-map 'honey.sql)
                    (scify/ns->ns-map 'honey.sql.protocols)
                    (scify/ns->ns-map 'honey.sql.helpers)

                    (scify/ns->ns-map 'clojure.zip)

                    ;; extras
                    { 'clojure.core.async babashka.impl.clojure.core.async/async-namespace
                     'clojure.core.async.impl.protocols babashka.impl.clojure.core.async/async-protocols-namespace
                     

                     'org.httpkit.client babashka.impl.httpkit-client/httpkit-client-namespace
                     'org.httpkit.sni-client babashka.impl.httpkit-client/sni-client-namespace
                     'org.httpkit.server babashka.impl.httpkit-server/httpkit-server-namespace

                     'clojure.data.xml babashka.impl.xml/xml-namespace
                     'clojure.data.xml.event babashka.impl.xml/xml-event-namespace
                     'clojure.data.xml.tree babashka.impl.xml/xml-tree-namespace

                     'next.jdbc babashka.impl.jdbc/njdbc-namespace
                     'next.jdbc.sql babashka.impl.jdbc/next-sql-namespace
                     'next.jdbc.result-set babashka.impl.jdbc/result-set-namespace
                     
                     ;; 'hiccup.core babashka.impl.hiccup/hiccup-namespace
                     ;; 'hiccup2.core babashka.impl.hiccup/hiccup2-namespace
                     ;; 'hiccup.util babashka.impl.hiccup/hiccup-util-namespace
                     ;; 'hiccup.compiler babashka.impl.hiccup/hiccup-compiler-namespace

                     'clojure.instant babashka.impl.clojure.instant/instant-namespace
                     }

                    (let [ns-name 'clojure.main
                          fns (sci/create-ns ns-name nil)]
                      {ns-name {'repl-requires
                                (sci/copy-var repl-requires fns)}}))}))

(let [current-ns-name (ns-name *ns*)]
  (defn new-sci-ctx []
    (let [sci-ctx (sci/init opts)]
      (sci/alter-var-root sci/out (constantly *out*))
      (sci/alter-var-root sci/err (constantly *err*))
      (alter-var-root
       #'membrane.component/*sci-ctx*
       (constantly sci-ctx))
      (sci/intern sci-ctx
                  (sci/find-ns sci-ctx current-ns-name)
                  'new-sci-ctx new-sci-ctx)
      sci-ctx)))

(def sci-ctx (delay
               (new-sci-ctx)
               #_(let [sci-ctx (sci/init opts)]
                   (sci/alter-var-root sci/out (constantly *out*))
                   (sci/alter-var-root sci/err (constantly *err*))
                   (alter-var-root
                    #'membrane.component/*sci-ctx*
                    (constantly sci-ctx))

                   sci-ctx)))


(defonce old-eval-msg babashka.nrepl.impl.server/eval-msg)

(defonce clear-future (atom nil))

(defn show-code [code]
  (when-let [fut @clear-future]
    (future-cancel fut)
    (reset! clear-future nil))

  (reset! debug-view
          (let [body (ui/padding
                      5 5
                      (ui/label code))
                [w h] (ui/bounds body)]
            (ui/translate 10 60
                          [(ui/with-color [1 1 1 0.8]
                             (ui/rectangle w h))
                           body])))

  (reset! clear-future
          (future
            (Thread/sleep 2000)
            (reset! debug-view nil))))

(comment
  (def server (babashka.nrepl.server/start-server! @sci-ctx {:host "0.0.0.0" :port 23456
                                                            :debug true
                                                            ;; :xform server-xform
                                                            #_#_ :xform
                                                             (comp babashka.nrepl.impl.middleware/wrap-read-msg
                                                                  babashka.nrepl.impl.server/wrap-process-message)}))
  (.close (:socket server))

  (require '[membrane.java2d :as backend])
  (backend/run #(deref debug-view))

  ,)


(defn with-background [body]
  (let [body (ui/padding 5 body)
        [w h] (ui/bounds body)]
    [(ui/filled-rectangle [1 1 1]
                          w h)
     body]))

 
(defn clj_init []
  (let [documents-dir
        (io/file
         (dt-ffi/c->string
          (objc
           [[[[NSFileManager defaultManager] :URLsForDirectory:inDomains
              ;; (int 14) ;; application support
              (int 9) ;; documents
              (int 1)
              ]
             :objectAtIndex 0]
            fileSystemRepresentation])))
        app-path (fs/file documents-dir
                          "scripts"
                          "app.clj")]
    (prn "documents dir" documents-dir)
    (when (fs/exists? app-path)
      (try
        (sci/eval-string* @sci-ctx (slurp app-path))
        (catch Exception e
          (prn e)))))

  (let [local-address (get-local-address)
        host-address (when local-address
                       (.getHostAddress ^InetAddress local-address))
        port 12345
        address-str (if host-address
                      (str host-address ":" 23456)
                      "No local address found.")]
    (reset! debug-view (ui/translate 10 50
                                     (with-background
                                       (ui/label address-str))))
    (println (str "address: \n" address-str))

    (babashka.nrepl.server/start-server! @sci-ctx
                                         {:host host-address :port 23456
                                          :xform server-xform})))

(defonce last-draw (atom nil))

(defn clj_needs_redraw []
  (if (not= @last-draw
            [@main-view @debug-view])
    1
    0))


(defn clj_draw [ctx]
  (try
    (let [mv @main-view
          dv @debug-view]
      (membrane.ios/skia_clear ctx)
      (membrane.ios/draw! ctx mv)
      (membrane.ios/draw! ctx dv)
      (reset! last-draw [mv dv]))
    (catch Exception e
      (prn e)
      (reset! last-draw nil))))

(defn clj_touch_ended [x y]
  (try
    (doall (ui/mouse-up @main-view [x y]))
    (catch Exception e
      (prn e))))

(defn clj_touch_began [x y]
  (try
    (doall (ui/mouse-down @main-view [x y]))
    (catch Exception e
      (prn e))))

(defn clj_touch_moved [x y]
  (try
    (let [view @main-view]
      (doall (ui/mouse-move view [x y]))
      (doall (ui/mouse-move-global view [x y])))
    (catch Exception e
      (prn e))))

(defn clj_touch_cancelled [x y])

(defn clj_insert_text [ptr]
  (let [s (dt-ffi/c->string ptr)]
    (try 
      (ui/key-press @main-view s)
      (catch Exception e
        (prn e)))))

(defn clj_delete_backward []
  (try
    (ui/key-press @main-view :backspace)
    (catch Exception e
      (prn e))))


(defn -main [& args])

(defn compile-interface-class [& args]
  ((requiring-resolve 'tech.v3.datatype.ffi.graalvm/expose-clojure-functions)
   {#'clj_init {:rettype :void}

    #'clj_needs_redraw {:rettype :int32}

    #'clj_draw {:rettype :void
                :argtypes [['skia-resource :pointer]]}
    #'clj_touch_ended {:rettype :void
                       :argtypes [['x :float64]
                                  ['y :float64]]}
    #'clj_touch_began {:rettype :void
                       :argtypes [['x :float64]
                                  ['y :float64]]}
    #'clj_touch_moved {:rettype :void
                       :argtypes [['x :float64]
                                  ['y :float64]]}
    #'clj_touch_cancelled {:rettype :void
                           :argtypes [['x :float64]
                                      ['y :float64]]}
    #'clj_delete_backward {:rettype :void}
    #'clj_insert_text {:rettype :void
                       :argtypes [['s :pointer]]}}

   'com.phronemophobic.grease.membrane.interface nil)
  )

(when *compile-files*
  (compile-interface-class))
