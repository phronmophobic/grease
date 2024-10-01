(ns com.phronemophobic.grease.replog
  (:require [com.phronemophobic.objcjure :refer [objc describe]
             :as objc]
            [babashka.fs :as fs]
            [tech.v3.datatype.ffi :as dt-ffi]
            [clojure.java.io :as io])
  )

(set! *warn-on-reflection* true)

(def documents-dir
  (delay
    (io/file
     (dt-ffi/c->string
      (objc [[[[NSFileManager defaultManager] :URLsForDirectory:inDomains
               ;; (int 14) ;; application support
               (int 9) ;; documents
               (int 1)
               ]
              :objectAtIndex 0]
             fileSystemRepresentation]))))) 



(defn append-form [ns form]
  )
(defn append-file [contents]
  )

(defn append-nrepl-op [op]
  (case (:op op)
    :load-file
    (let [{:keys [file-name ^String file]} op
          path (fs/file @documents-dir
                        "scripts"
                        file-name)]
      (fs/write-bytes path
                      (.getBytes file "utf-8")))


    ;; else
    nil))
