(ns app.webview
  (:require [com.phronemophobic.grease.ios.webview :as webview]))

(comment

  (def h
    (webview/open! {:url "https://google.com"
                    :inspectable? true
                    :functions {"echo" identity
                                "math" {"add" +}}}))

  (webview/eval-js! h "Grease.math.add(2, 3, 5, 30).then((res) => document.body.innerHTML = '' + res)"
                    (fn [err res]
                      (def result [err res])))

  (webview/eval-js! h "("
                    (fn [err res]
                      (def result [err res])))

  (webview/eval-js! h "5"
                    (fn [err res]
                      (def result [err res])))

  (webview/load-url! h "https://clojure.org")
  (webview/load-url! h "https://google.com")

  (webview/reload! h)
  (webview/go-back! h)
  (webview/close! h)

  ;;
  )
