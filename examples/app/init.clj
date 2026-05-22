(ns init
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [com.phronemophobic.grease.ios.deep-link :as deep-link]))

(defn debug-message! [_url]
  (println "debug deep link opened"))

(defn open-today! [calendar-id]
  ;; Put app behavior here.
  (println "open calendar:" calendar-id))

(defn eval-form! [url]
  (let [path (.getRawPath (java.net.URI/create url))]
    (when-not (str/blank? path)
      (eval (edn/read-string
             {:read-eval false}
             (java.net.URLDecoder/decode (subs path 1) "UTF-8"))))))

(def deep-link-handlers
  {"grease://debug" #'debug-message!
   "grease://eval" #'eval-form!})

(comment
  (deep-link/make-url "debug")

  (deep-link/make-url "eval" '(open-today! "work"))

  ;; To add a Home Screen shortcut:
  ;;
  ;; 1. Evaluate the open-shortcut-creator! form below.
  ;; 2. Grease copies the generated grease:// URL to the clipboard.
  ;; 3. Shortcuts opens a blank shortcut editor.
  ;; 4. Tap Add Action.
  ;; 5. Search for "Open URL" and add that action.
  ;; 6. Tap the URL field and paste the copied grease:// URL.
  ;; 7. Rename the shortcut.
  ;; 8. Open the shortcut details and choose Add to Home Screen.
  ;; 9. Save the Home Screen icon.
  ;;
  ;; iOS does not provide a documented URL parameter for pre-filling the
  ;; shortcut editor, so the paste step is still manual.
  (deep-link/open-shortcut-creator!
   (deep-link/make-url "eval" '(open-today! "work"))))
