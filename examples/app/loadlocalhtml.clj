(ns examples.app.loadlocalhtml
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [com.phronemophobic.grease.ios :as ios]
            [com.phronemophobic.grease.ios.webview :as webview]
            [com.phronemophobic.objcjure :refer [objc describe]
             :as objc]
            [tech.v3.datatype.ffi :as dt-ffi]))

(defn documents-dir []
  ;; fileSystemRepresentation
  (io/file
   (dt-ffi/c->string
    (objc [[[[NSFileManager defaultManager] :URLsForDirectory:inDomains
             ;; (int 14) ;; application support
             (int 9) ;; documents
             (int 1)]
            :objectAtIndex 0]
           fileSystemRepresentation]))))
(def scripts-dir
  (doto (fs/file  (documents-dir)
                  "scripts")
    fs/create-dirs))

(def html-dir
  (doto (fs/file  scripts-dir
                  "html")
    fs/create-dirs))

(def html
  "<html><body style=\"background-color:red\">Hello World</body></html>")

(defn -main [& args]
  (fs/write-bytes (fs/file
                   html-dir
                   "test.html")
                  (.getBytes html))
  (webview/open! {:url (io/as-url (fs/file
                                   html-dir
                                   "test.html"))}))
