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
            tech.v3.datatype.copy

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
           java.net.InetAddress
           java.time.ZonedDateTime
           java.util.Locale
           java.time.format.DateTimeFormatter
           java.security.MessageDigest
           [java.io
            BufferedWriter
            PrintWriter
            StringWriter
            Writer])
  (:gen-class))

(set! *warn-on-reflection* true)

(def main-view "The main view displayed. Edit at your own peril." (atom nil))
(defonce debug-view (atom nil))
(defonce debug-log (atom []))

(dt-ffi/define-library-interface
  {:clj_main_view {:rettype :pointer
                   :doc "Returns the UIView used to display the app."
                   :argtypes []}

   :clj_debug {:rettype :pointer
               :doc "Used for development. Noop."
               :argtypes [['data :pointer]]}
   ,})


(dt-struct/define-datatype!
  :CGPoint
  [{:name :x :datatype :float64}
   {:name :y :datatype :float64}])

(dt-struct/define-datatype!
  :CGSize
  [{:name :width :datatype :float64}
   {:name :height :datatype :float64}])

(dt-struct/define-datatype!
  :CGRect
  [{:name :origin :datatype :CGPoint}
   {:name :size :datatype :CGSize}])

(dt-struct/define-datatype!
  :UIEdgeInsets
  [{:name :top :datatype :float64}
   {:name :left :datatype :float64}
   {:name :bottom :datatype :float64}
   {:name :right :datatype :float64}])


(def UIAlertControllerStyleActionSheet 0)
(def UIAlertControllerStyleAlert 1)

(def UIAlertActionStyleDefault 0)
(def UIAlertActionStyleCancel 1)
(def UIAlertActionStyleDestructive 2)

(def main-queue (delay (ffi/dlsym ffi/RTLD_DEFAULT (dt-ffi/string->c "_dispatch_main_q"))))

(defn NSSetUncaughtExceptionHandler [handler]
  (ffi/call "NSSetUncaughtExceptionHandler" :void :pointer handler)
  )

(defn dispatch-main-async
  "Run `f` on the main queue."
  [f]
  (ffi/call "dispatch_async" :void :pointer @main-queue :pointer (objc
                                                                  (fn ^void []
                                                                    (try
                                                                      (f)
                                                                      (catch Exception e
                                                                        (println e)))))))

(defn prompt-bool
  "Displayes a prompt. Blocks until a response is given.

  Returns true/false."
  [{:keys [title message ok-text cancel-text]}]
  (let [p (promise)]
    (dispatch-main-async
     (fn []
       (let [title (if title
                     (objc/str->nsstring title)
                     (ffi/long->pointer 0))
             message (if message
                       (objc/str->nsstring message)
                       (ffi/long->pointer 0))
             ok-text (objc/str->nsstring (or ok-text "OK"))
             cancel-text (objc/str->nsstring (or cancel-text "Cancel"))
             
             alertController (objc [UIAlertController :alertControllerWithTitle:message:preferredStyle
                                    title
                                    message
                                    UIAlertControllerStyleAlert])
             
             cancelAction (objc
                           [UIAlertAction :actionWithTitle:style:handler
                            cancel-text
                            UIAlertActionStyleCancel
                            (fn ^void [action]
                              (deliver p false))])
             okAction (objc
                       [UIAlertAction :actionWithTitle:style:handler
                        ok-text
                        UIAlertActionStyleDefault
                        (fn ^void [action]
                          (deliver p true))])
             _ (do
                 (objc [alertController :addAction cancelAction])
                 (objc [alertController :addAction okAction]))

             vc (objc [[[UIApplication :sharedApplication] :keyWindow] :rootViewController])

             ]
         (objc [vc :presentViewController:animated:completion
                alertController true nil]))))
    @p))

(defn show-keyboard
  "Displays the keyboard."
  []
  (objc ^void
        [~(clj_main_view) :performSelectorOnMainThread:withObject:waitUntilDone
         ~(objc/sel_registerName (dt-ffi/string->c "becomeFirstResponder"))
         nil
         ~(byte 0)]))

(defn hide-keyboard
  "Hides the keyboard."
  []
  (objc ^void
        [~(clj_main_view) :performSelectorOnMainThread:withObject:waitUntilDone
         ~(objc/sel_registerName (dt-ffi/string->c "resignFirstResponder"))
         nil
         ~(byte 0)]))

(defn prompt-for-text
  "Shows an alert prompt with a text field.

  Returns immediately.
  
  `on-cancel` 0 arity function called when cancelled.
  `on-ok` ` 1 arity function called with the provided text"
  [{:keys [title message placeholder on-cancel on-ok cancel-text ok-text]}]
  (dispatch-main-async
   (fn []
     (let [title (if title
                   (objc/str->nsstring title)
                   (ffi/long->pointer 0))
           message (if message
                     (objc/str->nsstring message)
                     (ffi/long->pointer 0))
           ok-text (objc/str->nsstring (or ok-text "OK"))
           cancel-text (objc/str->nsstring (or cancel-text "Cancel"))
        
           alertController (objc [UIAlertController :alertControllerWithTitle:message:preferredStyle
                                  title
                                  message
                                  UIAlertControllerStyleAlert])
          
           _ (objc [alertController :addTextFieldWithConfigurationHandler
                    (fn ^void [textField]
                      (when placeholder
                        (objc [textField :setPlaceholder ~(objc/str->nsstring placeholder)])))])
           cancelAction (objc
                         [UIAlertAction :actionWithTitle:style:handler
                          cancel-text
                          UIAlertActionStyleCancel
                          ~(if on-cancel
                             (objc
                              (fn ^void [action]
                                (on-cancel)))
                             (ffi/long->pointer 0))])
           okAction (objc
                     [UIAlertAction :actionWithTitle:style:handler
                      ok-text
                      UIAlertActionStyleDefault
                      ~(if on-ok
                         (objc
                          (fn ^void [action]
                            (let [textField (objc
                                             [[alertController :textFields] :firstObject])
                                  inputText (objc [textField :text])]
                              (on-ok inputText))))
                         (ffi/long->pointer 0))])
           _ (do
               (objc [alertController :addAction cancelAction])
               (objc [alertController :addAction okAction]))

           vc (objc [[[UIApplication :sharedApplication] :keyWindow] :rootViewController])

           ]
       (objc [vc :presentViewController:animated:completion
              alertController true nil])))))


(defn bundle-dir
  "Returns a file for the bundle app directory. This directly can not be modified."
  []
    ;; NSBundle* mb = [NSBundle mainBundle];
    ;; return [[mb bundlePath] UTF8String];
  (io/file
   (dt-ffi/c->string
    (objc [[[NSBundle :mainBundle] :bundlePath] :UTF8String]))))

(defn documents-dir []
  "Returns a file for the documents directory. Support files for the application can be stored here."
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

(defn scripts-dir
  "Returns a file for the directory where the scripts are stored."
  []
  (fs/file (documents-dir)
           "scripts"))

(defmacro on-main
  "Synchronously runs `body` on the main thread and returns the result.

  If already running on the main thread, executes the body directly.
  Otherwise, enqueues the body on the main thread and waits for a result."
  [& body]
  `(if (objc ~(with-meta '[NSThread isMainThread]
                {:tag 'boolean}))
     (do ~@body)
     (let [p# (promise)]
       (dispatch-main-async
        (fn []
          (let [ret# (do ~@body)]
            (deliver p# ret#))))
       @p#)))

(def ^:private screen-bounds*
  (delay
    (on-main
     (objc ^CGRect [~(clj_main_view) bounds]))))
(defn screen-bounds
  "Returns the bounds for the app's view."
  []
  @screen-bounds*)
(defn screen-size
  "Returns the width and height of the current screen."
  []
  (:size @screen-bounds*))

(defn safe-area-insets
  "Returns safe area insets.

  The safe area of a view reflects the area not covered by navigation bars, tab bars, toolbars, and other ancestors that obscure a view controller's view."
  []
  (on-main
    (objc ^UIEdgeInsets [~(clj_main_view) :safeAreaInsets])))

(defn sleep [msecs]
  (Thread/sleep (long msecs)))


(defn get-local-address
  "Returns a local IP address as a string, if available."
  []
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


(defn get-addresses
  "Returns all possible host addresses for the network device."
  []
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

(def ^:private O_EVTONLY (int 0x00008000))
(def ^:private source-type-vnode (delay (ffi/dlsym ffi/RTLD_DEFAULT (dt-ffi/string->c "_dispatch_source_type_vnode"))))
(def ^:private  DISPATCH_VNODE_WRITE	0x2)
;; https://www.cocoanetics.com/2013/08/monitoring-a-folder-with-gcd/
(defn watch-directory
  "Watches the directory for changes and calls `f` with no arguments when a change is detected.

  Returns a 0 arity function that will stop watching when called."
  [path f]
  (let [;; _fileDescriptor = open([path fileSystemRepresentation], O_EVTONLY);
        fd (ffi/call "open" :int32
                     :pointer (dt-ffi/string->c
                               (str path))
                     :int32 O_EVTONLY)

        ;; _queue = dispatch_queue_create("DTFolderMonitor Queue", 0);
        file-queue (ffi/call "dispatch_queue_create"
                             :pointer
                             :pointer (dt-ffi/string->c
                                       "com.phronemophobic.FileWatcher")
                             :pointer (ffi/long->pointer 0))

        ;; _source = dispatch_source_create(DISPATCH_SOURCE_TYPE_VNODE, _fileDescriptor, DISPATCH_VNODE_WRITE, _queue);
        source (ffi/call "dispatch_source_create"
                         :pointer
                         :pointer @source-type-vnode
                         :int32 fd
                         :int64 DISPATCH_VNODE_WRITE
                         :pointer file-queue)

        ;; // call the passed block if the source is modified
        ;; dispatch_source_set_event_handler(_source, _block);
        _ (ffi/call "dispatch_source_set_event_handler"
                  :void
                  :pointer source
                  :pointer (objc/make-block
                            (fn []
                              (f))
                            :void
                            []))
 
        ;; // close the file descriptor when the dispatch source is cancelled
        ;; dispatch_source_set_cancel_handler(_source, ^{
 
        ;; 	close(_fileDescriptor);
        ;; });
        _ (ffi/call "dispatch_source_set_cancel_handler"
                  :void
                  :pointer source
                  :pointer (objc/make-block
                            (fn []
                              (ffi/call "close" :void :int32 fd))
                            :void
                            []))
 
        ;; // at this point the dispatch source is paused, so start watching
        ;; dispatch_resume(_source);
        _ (ffi/call "dispatch_resume"
                    :void
                    :pointer source)]
    (fn []
      ;; cleanup
      ;; dispatch_source_cancel(_source);
      (ffi/call "dispatch_source_cancel"
                :void
                :pointer source)
      
      ;; #if !OS_OBJECT_USE_OBJC
      ;; dispatch_release(_source);
      ;; #endif
      (ffi/call "dispatch_release"
                :void
                :pointer source)

      ;;       #if OS_OBJECT_USE_OBJC
      ;; 	dispatch_release(_queue);
      ;; #endif
      (ffi/call "dispatch_release"
                :void
                :pointer file-queue)
      nil)))


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


;; Broadcast messages sent to *out*
;; to nrepl server
(def *out*-original (delay *out*))
(def *err*-original (delay *err*))
(defonce sessions
  (atom {}))

(let [out (delay *out*)]
  (defn broadcast! [stream-key text]
    (doseq [[k send] @sessions]
      (try
        (send stream-key text)
        (catch Throwable e
          (binding [*out* @*out*-original]
            (println e))
          (swap! sessions dissoc k)
          )))))

(defn- to-char-array
  ^chars
  [x]
  (cond
    (string? x) (.toCharArray ^String x)
    (integer? x) (char-array [(char x)])
    :else x))

(defn wrap-writer [stream-key ^Writer wrapped-writer]
  (let [pw (-> (proxy [Writer] []
                 (write
                   ([x]
                    (let [cbuf (to-char-array x)]
                      (.write ^Writer this cbuf (int 0) (count cbuf))))
                   ([x off len]
                    (let [cbuf (to-char-array x)
                          text (str (doto (StringWriter.)
                                      (.write cbuf ^int off ^int len)))]
                      (when (pos? (count text))
                        (broadcast! stream-key text))
                      (.write wrapped-writer ^char/1 x (int off) (int len)))))
                 (flush [])
                 (close []))
               (BufferedWriter. 1024)
               (PrintWriter. true))]
    pw))

(defn setup-nrepl-logging! []
  
  (let [new-out (wrap-writer "out" *out*)
        new-err (wrap-writer "err" *err*)]
    ;; make sure these get set first
    @*out*-original
    @*err*-original
    (alter-var-root #'*out* (constantly new-out))
    (alter-var-root #'*err* (constantly new-err)))
  nil)

(def
  ^{::middleware/requires #{#'middleware/wrap-process-message
                            #'middleware/wrap-response-for}}
  pipe-logs
  (fn [rf]
    (let [session-id (Object.)
          last-session-info* (atom nil)]
      (swap! sessions
             assoc
             session-id
             (fn [stream-key text]
               (let [{:keys [result id session]} @last-session-info*]
                 (rf result
                     { ;; :response-for msg
                      :response {stream-key text
                                 "id" id
                                 "session" session}}))))
      (fn
        ([] (rf))
        ([result]
         (swap! sessions
                dissoc session-id)
         (rf result))
        ([result {:keys [response] :as input}]
         (let [{:strs [session id]} response]
           (when (and session id result)
             (reset! last-session-info*
                     {:result result
                      :session session
                      :id id})))
         (rf result input))))))

;; Add cross cutting middleware
(def server-xform
  (middleware/middleware->xform
   (conj middleware/default-middleware
         #'pipe-logs
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
              'java.time.ZonedDateTime java.time.ZonedDateTime
              'java.util.Locale java.util.Locale
              'java.time.format.DateTimeFormatter java.time.format.DateTimeFormatter
              'java.security.MessageDigest java.security.MessageDigest
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
                    (scify/ns->ns-map 'tech.v3.datatype.copy)

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
  (let [scripts-dir (fs/file  (documents-dir)
                              "scripts")]
    (when (not (fs/exists? scripts-dir))
      (fs/create-dirs scripts-dir))

    ;; copy gol example
    (let [gol-bundle-path (fs/file (bundle-dir)
                                   "gol.clj")
          gol-script-path (fs/file scripts-dir
                                   "gol.clj")]
      (when (not (fs/exists? gol-script-path))
        (fs/copy gol-bundle-path
                 gol-script-path)))

    (let [app-bundle-path (fs/file (bundle-dir)
                                   "app.clj")
          app-script-path (fs/file scripts-dir
                                   "app.clj")
          app-path (if (fs/exists? app-script-path)
                     app-script-path
                     app-bundle-path)]
      (when (fs/exists? app-path)
        (try
          (sci/eval-string* @sci-ctx (slurp app-path))
          (catch Exception e
            (prn e)))))

    (setup-nrepl-logging!))

  #_(let [local-address (get-local-address)
          host-address (when local-address
                         (.getHostAddress ^InetAddress local-address))
          port 12345
          address-str (if host-address
                        (str host-address ":" 23456)
                        "No local address found.")]
      (reset! debug-view (ui/translate 10 50
                                       (with-background
                                         (ui/label address-str))))
      (setup-nrepl-logging!)
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
