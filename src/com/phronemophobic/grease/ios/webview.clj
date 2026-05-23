(ns com.phronemophobic.grease.ios.webview
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [com.phronemophobic.grease.ios.utils :as utils
             :refer [on-main]]
            [com.phronemophobic.objcjure :as objc :refer [objc]]))

(deftype ^:private WebViewHandle [controller registry handler])

(defn- handle-controller [handle]
  (when-not (instance? WebViewHandle handle)
    (throw (ex-info "Expected a webview handle."
                    {:value handle})))
  (.-controller ^WebViewHandle handle))

(defn- url-string [url op]
  (let [url (some-> url str)]
    (when (str/blank? url)
      (throw (ex-info (str (name op) " requires a non-blank URL string.")
                      {:url url
                       :op op})))
    url))

(defn- javascript-string [javascript]
  (let [javascript (some-> javascript str)]
    (when (str/blank? javascript)
      (throw (ex-info "eval-js! requires a non-blank JavaScript string."
                      {:javascript javascript})))
    javascript))

(def ^:private reserved-function-names
  #{"__nativeBridgeVersion" "_nativeCallback" "__proto__" "constructor" "prototype"})

(defn- json-key [k]
  (if (keyword? k)
    (name k)
    (str k)))

(defn- json-value [x]
  (cond
    (nil? x) nil
    (or (string? x)
        (number? x)
        (true? x)
        (false? x)) x
    (keyword? x) (name x)
    (map? x) (into {}
                   (map (fn [[k v]]
                          [(json-key k) (json-value v)]))
                   x)
    (sequential? x) (mapv json-value x)
    :else (throw (ex-info "Bridge return value is not JSON-compatible."
                          {:value x}))))

(defn- normalize-functions
  ([functions]
   (normalize-functions [] functions))
  ([path functions]
   (when-not (map? functions)
     (throw (ex-info "webview/open! :functions must be a map."
                     {:path path
                      :value functions})))
   (reduce-kv
    (fn [{:keys [tree registry]} k v]
      (when-not (string? k)
        (throw (ex-info "webview/open! :functions keys must be strings."
                        {:path path
                         :key k})))
      (when (contains? reserved-function-names k)
        (throw (ex-info "webview/open! :functions contains a reserved JavaScript name."
                        {:path path
                         :key k})))
      (let [child-path (conj path k)]
        (cond
          (map? v)
          (let [{child-tree :tree child-registry :registry}
                (normalize-functions child-path v)]
            {:tree (assoc tree k child-tree)
             :registry (merge registry child-registry)})

          (ifn? v)
          {:tree (assoc tree k true)
           :registry (assoc registry child-path v)}

          :else
          (throw (ex-info "webview/open! :functions values must be functions or nested maps."
                          {:path child-path
                           :value v})))))
    {:tree {}
     :registry {}}
    functions)))

(defn- bridge-response [ok status value]
  (json/write-str {:ok ok
                   :status status
                   :value (json-value value)}))

(defn- invoke-registered-function [registry payload-json]
  (try
    (let [{:strs [path args]} (json/read-str (objc/nsstring->str payload-json))
          path (vec path)
          f (get registry path)]
      (if f
        (bridge-response true "ok" (apply f args))
        (bridge-response false
                         "function_not_found"
                         {:message (str "No WebView function registered at "
                                        (str/join "." path))})))
    (catch Throwable e
      (bridge-response false
                       "exception"
                       {:message (or (ex-message e)
                                     (str e))}))))

(defn- bridge-handler [registry]
  (objc (fn ^pointer [payload-json]
          (objc/str->nsstring (invoke-registered-function registry payload-json)))))

(defn- file-url [path]
  (let [path (url-string path :load-file-url!)]
    (if (re-find #"^[A-Za-z][A-Za-z0-9+.-]*:" path)
      (objc [NSURL :URLWithString ~(objc/str->nsstring path)])
      (objc [NSURL :fileURLWithPath ~(objc/str->nsstring path)]))))

(defn- arc-object [o]
  (when-not (utils/nil-pointer? o)
    (objc/arc! o)))

(defn- generic-callback [callback value-fn]
  (if callback
    (objc (fn ^void [value error]
            (callback
             (utils/objc->clj error)
             (when-not (utils/nil-pointer? value)
               (value-fn value)))))
    utils/nil-pointer))

(defn- native-callback [callback]
  (generic-callback callback objc/arc!))

(defn- js-callback [callback]
  (generic-callback callback utils/objc->clj))

(defn- controller-open? [controller]
  (zero? (long (on-main
                (objc ^byte [controller :isClosed])))))

(def ^:private content-inset-adjustment-behaviors
  {:automatic 0
   :scrollable-axes 1
   :never 2
   :always 3})

(defn- content-inset-adjustment-behavior-value [behavior]
  (when behavior
    (if-let [value (get content-inset-adjustment-behaviors behavior)]
      value
      (throw (ex-info "webview/open! :content-inset-adjustment-behavior must be one of :automatic, :scrollable-axes, :never, or :always."
                      {:value behavior})))))

(defn- ensure-open! [handle op]
  (let [controller (handle-controller handle)]
    (when-not (controller-open? controller)
      (throw (ex-info "Web view has already been closed."
                      {:op op})))
    controller))

(defn- web-view [handle op]
  (let [controller (ensure-open! handle op)]
    (on-main
     (objc [controller :webView]))))

(defn open!
  "Opens a full-screen web view and returns an opaque webview handle.

  Accepts a map with `:url`, optional recursive `:functions`, and optional
  `:safe-area?`, `:inspectable?`, and `:content-inset-adjustment-behavior`.

  When `:safe-area?` is true, the WKWebView is pinned to its container view's
  safeAreaLayoutGuide instead of the full container bounds.

  When `:inspectable?` is true, the WKWebView will allow Safari Web Inspector 
  access to inspect the view’s content. Then, select your view in Safari’s Develop
  menu for either your computer or an attached device to inspect it.

  When `:content-inset-adjustment-behavior` is provided, it sets the native
  WKWebView scroll view's contentInsetAdjustmentBehavior. Accepted values are
  `:automatic`, `:scrollable-axes`, `:never`, and `:always`."
  [{:keys [url functions safe-area? inspectable? content-inset-adjustment-behavior] :as args}]
  (let [url (url-string url :open!)
        inset-adjustment-behavior (content-inset-adjustment-behavior-value
                                   content-inset-adjustment-behavior)
        {:keys [tree registry]} (normalize-functions (or functions {}))
        native-tree (utils/native-dictionary tree)
        handler (bridge-handler registry)
        controller (on-main
                    (let [controller (objc [[GreaseWebOverlayController :alloc]
                                            :initWithURLString:functionTree:handler
                                            ~(objc/str->nsstring url)
                                            native-tree
                                            handler])
                          _ (objc ^void [controller :setWebViewUsesSafeAreaLayoutGuide
                                         ~(byte (if safe-area? 1 0))])
                          _ (objc ^void [controller :setWebViewInspectable
                                         ~(byte (if inspectable? 1 0))])
                          _ (when inset-adjustment-behavior
                              (objc ^void [controller
                                           :setWebViewContentInsetAdjustmentBehavior
                                           ~(long inset-adjustment-behavior)]))
                          loaded? (objc ^byte [controller :loadURLString
                                               ~(objc/str->nsstring url)])]
                      (when (zero? (long loaded?))
                        (throw (ex-info "Could not load webview URL."
                                        {:args args})))
                      (let [presented? (objc ^byte [controller :presentInViewController
                                                    ~(objc [[[UIApplication :sharedApplication]
                                                             :keyWindow]
                                                            :rootViewController])])]
                        (when (zero? (long presented?))
                          (throw (ex-info "Could not find a root view controller for the webview."
                                          {:args args}))))
                      controller))]
    (WebViewHandle. controller registry handler)))

(defn close!
  "Closes the web view.

  Throws if the web view is already closed."
  [handle]
  (let [controller (handle-controller handle)
        closed? (on-main
                 (objc ^byte [controller :close]))]
    (when (zero? (long closed?))
      (throw (ex-info "Web view has already been closed."
                      {:op :close!})))
    nil))

(defn closed?
  "Returns true when the web view handle has been closed."
  [handle]
  (not (controller-open? (handle-controller handle))))

(defn load-url!
  "Loads a URL string in the web view."
  [handle url]
  (let [controller (ensure-open! handle :load-url!)
        url (url-string url :load-url!)
        loaded? (on-main
                 (objc ^byte [controller :loadURLString
                              ~(objc/str->nsstring url)]))]
    (when (zero? (long loaded?))
      (throw (ex-info "Could not load webview URL."
                      {:url url})))
    nil))

(defn load-request!
  "Loads a native NSURLRequest in the web view."
  [handle request]
  (on-main
   (arc-object (objc [~(web-view handle :load-request!) :loadRequest request]))))

(defn load-html!
  "Loads an HTML string in the web view.

  Optionally accepts a base URL string."
  ([handle html]
   (load-html! handle html nil))
  ([handle html base-url]
   (let [html (str html)
         base-url (if base-url
                    (objc [NSURL :URLWithString ~(objc/str->nsstring (str base-url))])
                    utils/nil-pointer)]
     (on-main
      (arc-object (objc [~(web-view handle :load-html!) :loadHTMLString:baseURL
                         ~(objc/str->nsstring html)
                         base-url]))))))

(defn load-file-url!
  "Loads a file URL or path, allowing read access to another file URL or path."
  [handle file-url-or-path allowing-read-access-url-or-path]
  (on-main
   (arc-object (objc [~(web-view handle :load-file-url!) :loadFileURL:allowingReadAccessToURL
                      ~(file-url file-url-or-path)
                      ~(file-url allowing-read-access-url-or-path)]))))

(defn reload!
  "Reloads the current page."
  [handle]
  (on-main
   (arc-object (objc [~(web-view handle :reload!) :reload]))))

(defn reload-from-origin!
  "Reloads the current page, validating cached content against its origin."
  [handle]
  (on-main
   (arc-object (objc [~(web-view handle :reload-from-origin!) :reloadFromOrigin]))))

(defn stop-loading!
  "Stops loading the current page."
  [handle]
  (on-main
   (objc ^void [~(web-view handle :stop-loading!) :stopLoading]))
  nil)

(defn go-back!
  "Navigates to the previous item in the back-forward list."
  [handle]
  (on-main
   (arc-object (objc [~(web-view handle :go-back!) :goBack]))))

(defn go-forward!
  "Navigates to the next item in the back-forward list."
  [handle]
  (on-main
   (arc-object (objc [~(web-view handle :go-forward!) :goForward]))))

(defn go-to!
  "Navigates to a native WKBackForwardListItem."
  [handle back-forward-list-item]
  (on-main
   (arc-object (objc [~(web-view handle :go-to!)
                      :goToBackForwardListItem back-forward-list-item]))))

(defn eval-js!
  "Evaluates JavaScript in the web view.

  With a callback, calls it with `error` and `result` arguments.
  JavaScript values are converted to Clojure data."
  ([handle javascript]
   (eval-js! handle javascript nil))
  ([handle javascript callback]
   (let [javascript (javascript-string javascript)
         callback (js-callback callback)]
     (on-main
      (objc ^void [~(web-view handle :eval-js!) :evaluateJavaScript:completionHandler
                   ~(objc/str->nsstring javascript)
                   callback]))
     nil)))

(defn call-async-js!
  "Calls asynchronous JavaScript in the page content world.

  `arguments` must be a native NSDictionary or nil.
  The callback receives `error` and `result` arguments.
  JavaScript values are converted to Clojure data."
  [handle javascript arguments callback]
  (let [javascript (javascript-string javascript)
        arguments (or arguments utils/nil-pointer)
        callback (js-callback callback)]
    (on-main
     (objc ^void [~(web-view handle :call-async-js!)
                  :callAsyncJavaScript:arguments:inFrame:inContentWorld:completionHandler
                  ~(objc/str->nsstring javascript)
                  arguments
                  ~utils/nil-pointer
                  ~(objc [WKContentWorld :pageWorld])
                  callback]))
    nil))

(defn take-snapshot!
  "Takes a snapshot of the web view.

  Optionally accepts a native WKSnapshotConfiguration before the callback.
  The callback receives `error` and `image` arguments."
  ([handle callback]
   (take-snapshot! handle nil callback))
  ([handle configuration callback]
   (on-main
    (objc ^void [~(web-view handle :take-snapshot!)
                 :takeSnapshotWithConfiguration:completionHandler
                 ~(or configuration utils/nil-pointer)
                 ~(native-callback callback)]))
   nil))

(defn create-pdf!
  "Creates PDF data for the web view.

  Optionally accepts a native WKPDFConfiguration before the callback.
  The callback receives `error` and `data` arguments."
  ([handle callback]
   (create-pdf! handle nil callback))
  ([handle configuration callback]
   (on-main
    (objc ^void [~(web-view handle :create-pdf!)
                 :createPDFWithConfiguration:completionHandler
                 ~(or configuration utils/nil-pointer)
                 ~(native-callback callback)]))
   nil))

(defn create-web-archive-data!
  "Creates web archive data for the current page.

  The callback receives `error` and `data` arguments."
  [handle callback]
  (on-main
   (objc ^void [~(web-view handle :create-web-archive-data!)
                :createWebArchiveDataWithCompletionHandler
                ~(native-callback callback)]))
  nil)

(defn can-go-back?
  "Returns true if the web view can navigate backward."
  [handle]
  (not (zero? (long (on-main
                     (objc ^byte [~(web-view handle :can-go-back?) :canGoBack]))))))

(defn can-go-forward?
  "Returns true if the web view can navigate forward."
  [handle]
  (not (zero? (long (on-main
                     (objc ^byte [~(web-view handle :can-go-forward?) :canGoForward]))))))

(defn loading?
  "Returns true if the web view is currently loading."
  [handle]
  (not (zero? (long (on-main
                     (objc ^byte [~(web-view handle :loading?) :isLoading]))))))

(defn url
  "Returns the current URL string, or nil."
  [handle]
  (on-main
   (utils/NSURL->str (objc [~(web-view handle :url) :URL]))))

(defn title
  "Returns the current page title, or nil."
  [handle]
  (on-main
   (utils/NSString->str (objc [~(web-view handle :title) :title]))))

(defn estimated-progress
  "Returns the estimated page load progress from 0.0 to 1.0."
  [handle]
  (on-main
   (objc ^double [~(web-view handle :estimated-progress) :estimatedProgress])))

(defn has-only-secure-content?
  "Returns true if all currently loaded resources use secure origins."
  [handle]
  (not (zero? (long (on-main
                     (objc ^byte [~(web-view handle :has-only-secure-content?)
                                  :hasOnlySecureContent]))))))

(defn server-trust
  "Returns the current native SecTrust object, or nil."
  [handle]
  (let [trust (on-main
               (objc [~(web-view handle :server-trust) :serverTrust]))]
    (when-not (utils/nil-pointer? trust)
      trust)))

(defn back-forward-list
  "Returns the native WKBackForwardList."
  [handle]
  (on-main
   (arc-object (objc [~(web-view handle :back-forward-list) :backForwardList]))))

(defn scroll-view
  "Returns the native UIScrollView used by the web view."
  [handle]
  (on-main
   (arc-object (objc [~(web-view handle :scroll-view) :scrollView]))))

(defn configuration
  "Returns the native WKWebViewConfiguration."
  [handle]
  (on-main
   (arc-object (objc [~(web-view handle :configuration) :configuration]))))

(defn custom-user-agent
  "Returns the custom user agent string, or nil."
  [handle]
  (on-main
   (utils/NSString->str (objc [~(web-view handle :custom-user-agent) :customUserAgent]))))

(defn set-custom-user-agent!
  "Sets the custom user agent string.

  Pass nil to restore the default user agent."
  [handle user-agent]
  (on-main
   (objc ^void [~(web-view handle :set-custom-user-agent!)
                :setCustomUserAgent
                ~(utils/nullable-nsstring user-agent)]))
  nil)

(defn allows-back-forward-navigation-gestures?
  "Returns true if horizontal swipe gestures navigate back and forward."
  [handle]
  (not (zero? (long (on-main
                     (objc ^byte [~(web-view handle :allows-back-forward-navigation-gestures?)
                                  :allowsBackForwardNavigationGestures]))))))

(defn set-allows-back-forward-navigation-gestures!
  "Enables or disables horizontal swipe navigation gestures."
  [handle enabled?]
  (on-main
   (objc ^void [~(web-view handle :set-allows-back-forward-navigation-gestures!)
                :setAllowsBackForwardNavigationGestures
                ~(byte (if enabled? 1 0))]))
  nil)

(defn allows-link-preview?
  "Returns true if link previews are enabled."
  [handle]
  (not (zero? (long (on-main
                     (objc ^byte [~(web-view handle :allows-link-preview?)
                                  :allowsLinkPreview]))))))

(defn set-allows-link-preview!
  "Enables or disables link previews."
  [handle enabled?]
  (on-main
   (objc ^void [~(web-view handle :set-allows-link-preview!)
                :setAllowsLinkPreview
                ~(byte (if enabled? 1 0))]))
  nil)

(defn page-zoom
  "Returns the page zoom factor."
  [handle]
  (on-main
   (objc ^double [~(web-view handle :page-zoom) :pageZoom])))

(defn set-page-zoom!
  "Sets the page zoom factor."
  [handle zoom]
  (on-main
   (objc ^void [~(web-view handle :set-page-zoom!) :setPageZoom ~(double zoom)]))
  nil)

(defn media-type
  "Returns the CSS media type override, or nil."
  [handle]
  (on-main
   (utils/NSString->str (objc [~(web-view handle :media-type) :mediaType]))))

(defn set-media-type!
  "Sets the CSS media type override.

  Pass nil to use the default media type."
  [handle media-type]
  (on-main
   (objc ^void [~(web-view handle :set-media-type!)
                :setMediaType
                ~(utils/nullable-nsstring media-type)]))
  nil)
