(ns com.phronemophobic.grease
  (:require [tech.v3.datatype.ffi :as dt-ffi]
            [sci.core :as sci]
            [sci.addons :as addons]
            babashka.nrepl.server)
  (:import org.graalvm.nativeimage.c.function.CEntryPointLiteral
           tech.v3.datatype.ffi.Pointer
           org.graalvm.word.WordBase)
  
  (:gen-class))
(set! *warn-on-reflection* true)


(extend-protocol dt-ffi/PToPointer
  ;; org.graalvm.word.WordBase
  CEntryPointLiteral
  (convertible-to-pointer? [item] true)
  (->pointer [item] (tech.v3.datatype.ffi.Pointer. (.rawValue (.getFunctionPointer item)))))

(defn clj_sub [a b]
  (- a b))

(defn clj_add [a b]
  (+ a b))

(defn clj_print [bs]
  (prn (dt-ffi/c->string bs)))

(def results (atom {:results {}
                    :id 0}))

(defn add-result [obj]
  (:id
   (swap! results
          (fn [{:keys [results id]}]
            (let [newid (inc id)]
              {:results (assoc results newid obj)
               :id newid})))))

(defn get-result [id]
  (get-in @results [:results id]))

(defn clj_prn [id]
  (prn (get-result id)))

(defn clj_eval [bs]
  (add-result (sci/eval-string (dt-ffi/c->string bs))))

(defn clj_print_hi []
  (println "hi"))


(def opts (-> {:namespaces {'foo.bar {'x 1}}}
              addons/future))
(def sci-ctx (sci/init opts))

(defn clj_start_server []
  (babashka.nrepl.server/start-server! sci-ctx {:host "0.0.0.0" :port 23456}))

(defn clj_callback_fn []
  (println "hello callback"))

(defn compile-interface-class
  ([]
   (compile-interface-class nil))
  ([opts]
   ((requiring-resolve 'tech.v3.datatype.ffi.graalvm/expose-clojure-functions)
    {
     #'clj_sub {:rettype :int64
                :argtypes [['a :int64]
                           ['b :int64]
                           ]}

     #'clj_add {:rettype :int64
                :argtypes [['a :int64]
                           ['b :int64]]}

     #'clj_print {:rettype :void
                  :argtypes [['bs :pointer]]}

     #'clj_prn {:rettype :void
                :argtypes [['bs :int64]]}

     #'clj_eval {:rettype :int64
                 :argtypes [['bs :pointer]]}
     #'clj_start_server {:rettype :void
                         :argtypes []}

     #'clj_print_hi {:rettype :void
                     :argtypes []}

     #'clj_callback_fn {:rettype :void
                        :argtypes []}}
    'com.phronemophobic.grease.interface nil))
  )

(when *compile-files*
  (compile-interface-class))

