(ns com.phronemophobic.grease.scify
  (:require [sci.core :as sci]
            [sci.impl.types :as types]))


(defn make-protocol-sci-compatible [proto-var]
  (let [sci-compatible? (-> proto-var meta ::sci-compatible?)]
    (when-not sci-compatible?
      (alter-meta! proto-var
                   assoc
                   ::sci-compatible-protocol true)

      (doseq [method-var (keys (:method-builders @proto-var))]
        (let [old-impl @method-var
              method-name (.sym method-var)
              new-impl (new clojure.lang.MultiFn
                            (name (gensym (name method-name)))
                            types/type-impl
                            :default
                            #'clojure.core/global-hierarchy)]
          (. new-impl
             clojure.core/addMethod
             :sci.impl.protocols/reified
             (fn [this & args]
               (let [method (-> (types/getMethods this)
                                (get method-name))]
                 (apply method this args))))

          (. new-impl
             clojure.core/addMethod
             :default
             (fn [this & args]
               (apply old-impl this args)))

          (alter-meta! method-var
                       assoc ::original-impl old-impl)
          (alter-var-root method-var
                          (constantly new-impl)))))))

(defn scify-ns-protocols [ns-name]
  (doseq [[sym var] (ns-publics ns-name)]
    (when (every? #(% @var)
                  [:on :on-interface :sigs :method-builders])
      (make-protocol-sci-compatible var))))

(defn ns->ns-map [ns-name]
  (let [sci-ns-var (sci/create-ns ns-name nil)]
    {ns-name
     (reduce (fn [ns-map [var-name var]]
               (let [m (meta var)
                     var-val (if (::sci-compatible-protocol m)
                               {:methods
                                (into #{}
                                      (->> @var
                                           :sigs
                                           vals
                                           (map :name)))
                                :ns sci-ns-var}
                               @var)
                     ]
                 
                 (assoc ns-map var-name
                        (sci/new-var (symbol var-name) var-val
                                     (assoc (meta var)
                                            :ns sci-ns-var)))))
             {}
             (ns-publics ns-name))}))
