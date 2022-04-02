(ns com.phronemophobic.grease.graalvm
  "Helper functions for listing clojure classes to pass to --initialize-at-build-time"
  (:require [clojure.string :as str]))


(defn initialize-at-build-time-list [& args]
  (println
   (->> (map ns-name (all-ns))
        (remove #(clojure.string/starts-with? % "clojure"))
        (map #(clojure.string/split (str %) #"\."))
        (keep butlast)
        (map #(clojure.string/join "." %))
        distinct
        (map munge)
        (cons "clojure")
        (clojure.string/join ","))))



(defn list-resources [path]
  (let [jar (java.util.jar.JarFile. path)  
        entries (.entries jar)]
    (loop [result  []]
      (if (.hasMoreElements entries)
        (recur (conj result (.. entries nextElement getName)))
        result))))

(defn namespace->namespace-key [ns]
  (-> ns
      ns-name
      str
      (clojure.string/split #"\.")
      (->> (map munge))))

(defn class-path->namespace-key [fname]
  (-> fname
      (clojure.string/replace #"(\$[^.]+)?.class$" "")
      (clojure.string/split #"/"))
  )

(defn class-path->classname [fname]
  (-> (subs fname 0 (- (count fname)
                       (count ".class")))
      (clojure.string/replace #"/" ".")))

(defn clojure-classes-in-jar [jar-path]
  (let [resources (list-resources jar-path)
        ns-keys (into
                 #{}
                 (map namespace->namespace-key)
                 (all-ns))
        classes (into #{}
                      (comp
                       (filter #(clojure.string/ends-with? % ".class"))
                       (filter (fn [class-path]
                                 (contains? ns-keys (class-path->namespace-key class-path))))
                       (map class-path->classname))
                      resources)]
    classes))

(comment

  (->> (map ns-name (all-ns))
       (remove #(str/starts-with? % "clojure"))
       (map #(str/split (str %) #"\."))
       (keep butlast)
       (map #(str/join "." %))
       distinct
       (map munge)
       (cons "clojure")))
