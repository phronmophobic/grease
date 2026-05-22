(ns com.phronemophobic.grease.ios.deep-link
  (:require [clojure.string :as str]
            [com.phronemophobic.grease.ios.utils :refer [on-main]]
            [com.phronemophobic.objcjure :as objc :refer [objc]])
  (:import [java.net URLEncoder]
           [java.nio.charset StandardCharsets]))

(defn- path-segment-encode [s]
  (str/replace (URLEncoder/encode (str s) (.name StandardCharsets/UTF_8))
               "+"
               "%20"))

(defn make-url
  "Builds a grease:// URL.

  `path` is appended after grease://. When `form` is provided, it is written
  as EDN and URL-encoded as the first path segment after `path`."
  ([path]
   (str "grease://" (str/replace (str path) #"^/+" "")))
  ([path form]
   (str (make-url path)
        "/"
        (path-segment-encode (pr-str form)))))

(defn shortcut-creator-url
  "Builds the documented Shortcuts URL for opening a blank shortcut editor.

  Apple does not document query parameters that pre-fill the shortcut name or
  actions on iOS 16."
  []
  "shortcuts://create-shortcut")

(defn open-shortcut-creator!
  "Copies `grease-url` to the clipboard and opens a blank Shortcuts editor.

  iOS 16 Shortcuts does not provide a documented URL parameter for pre-filling
  the new shortcut. In Shortcuts, add an Open URL action, paste the copied
  grease:// URL, then add that shortcut to the Home Screen."
  [grease-url]
  (let [grease-url (str grease-url)
        shortcuts-url (shortcut-creator-url)]
    (on-main
     (objc ^void [[UIPasteboard generalPasteboard] :setString
                  ~(objc/str->nsstring grease-url)])
     (objc ^void [[UIApplication :sharedApplication]
                  :openURL:options:completionHandler
                  ~(objc [NSURL :URLWithString ~(objc/str->nsstring shortcuts-url)])
                  ~(objc [NSDictionary :dictionary])
                  nil]))
    grease-url))
