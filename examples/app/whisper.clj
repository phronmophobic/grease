(ns examples.app.whisper
  (:require [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.phronemophobic.grease.ios :as ios]
            [com.phronemophobic.grease.ios.whisper :as whisper])
  (:import [java.net URL]
           [java.util.zip ZipInputStream]))

(def whisper-model "small.en-q5_1")

(defn model-dir []
  (fs/file (ios/documents-dir) "whisper" whisper-model))

(defn model-path []
  (str (fs/file (model-dir) "model.bin")))

(defn- encoder-model []
  (str/replace whisper-model #"-q[0-9]_[0-9]$" ""))

(defn- model-url []
  (str "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-"
       whisper-model
       ".bin?download=true"))

(defn- encoder-url []
  (str "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-"
       (encoder-model)
       "-encoder.mlmodelc.zip?download=true"))

(defn- println-flush! [& xs]
  (apply println xs)
  (flush))

(defn- format-bytes [n]
  (format "%.1f MB" (/ (double n) 1048576.0)))

(defn- print-download-progress!
  [{:keys [label downloaded total done?]}]
  (let [prefix (if done? "Downloaded" "Downloading")]
    (if (pos? total)
      (println-flush! (str prefix " " label ": "
                           (quot (* downloaded 100) total)
                           "% ("
                           (format-bytes downloaded)
                           " / "
                           (format-bytes total)
                           ")"))
      (println-flush! (str prefix " " label ": "
                           (format-bytes downloaded))))))

(defn- make-download-progress-printer []
  (let [out *out*]
    (fn [progress]
      (binding [*out* out]
        (print-download-progress! progress)))))

(defn- copy-with-progress! [in out label total progress!]
  (let [buffer (byte-array (* 128 1024))]
    (loop [downloaded 0
           last-percent -5
           last-bytes 0]
      (let [n (.read in buffer)]
        (if (neg? n)
          (do
            (progress! {:label label
                        :downloaded downloaded
                        :total total
                        :done? true})
            downloaded)
          (let [downloaded' (+ downloaded n)
                report-percent? (pos? total)
                percent (when report-percent?
                          (min 100 (quot (* downloaded' 100) total)))
                report? (if report-percent?
                          (>= percent (+ last-percent 5))
                          (>= (- downloaded' last-bytes) 1048576))]
            (.write out buffer 0 n)
            (when report?
              (progress! {:label label
                          :downloaded downloaded'
                          :total total
                          :done? false}))
            (recur downloaded'
                   (if (and report-percent? report?)
                     percent
                     last-percent)
                   (if (and (not report-percent?) report?)
                     downloaded'
                     last-bytes))))))))

(defn- download-file! [url destination label progress!]
  (let [destination (fs/file destination)
        tmp (fs/file (str destination ".tmp"))]
    (if (fs/exists? destination)
      (println-flush! "Already downloaded" label ":" (str destination))
      (do
        (fs/create-dirs (fs/parent destination))
        (fs/delete-if-exists tmp)
        (with-open [in (.openStream (URL. url))
                    out (io/output-stream tmp)]
          (copy-with-progress! in out label -1 progress!))
        (fs/move tmp destination {:replace-existing true}))))
  destination)

(defn- unzip-mlmodelc! [zip-path destination]
  (let [destination (fs/file destination)
        tmp (fs/file (str destination ".tmp"))]
    (when-not (fs/exists? destination)
      (when (fs/exists? tmp)
        (fs/delete-tree tmp))
      (fs/create-dirs tmp)
      (with-open [zip (ZipInputStream. (io/input-stream zip-path))]
        (loop [entry (.getNextEntry zip)]
          (when entry
            (let [name (.getName entry)
                  marker ".mlmodelc/"
                  marker-index (.indexOf name marker)]
              (when (and (not (.isDirectory entry))
                         (not (neg? marker-index)))
                (let [relative-name (subs name (+ marker-index (count marker)))
                      output-file (fs/file tmp relative-name)]
                  (when-not (str/blank? relative-name)
                    (fs/create-dirs (fs/parent output-file))
                    (with-open [out (io/output-stream output-file)]
                      (io/copy zip out))))))
            (.closeEntry zip)
            (recur (.getNextEntry zip)))))
      (fs/move tmp destination {:replace-existing true})))
  destination)

(defn ensure-whisper-model!
  ([]
   (ensure-whisper-model! (make-download-progress-printer)))
  ([progress!]
   (let [dir (model-dir)
         encoder-zip (fs/file dir "model-encoder.mlmodelc.zip")]
     (fs/create-dirs dir)
     (download-file! (model-url) (model-path) "Whisper model" progress!)
     (download-file! (encoder-url) encoder-zip "Core ML encoder" progress!)
     (unzip-mlmodelc! encoder-zip (fs/file dir "model-encoder.mlmodelc"))
     (model-path))))

(defn ensure-whisper-model-async! []
  (let [out *out*
        err *err*]
    (future
      (binding [*out* out
                *err* err]
        (ensure-whisper-model!)))))

(defn start-example-dictation! []
  (whisper/start-dictation! {:model-path (ensure-whisper-model!)}))

(defn stop-example-dictation! []
  (whisper/stop-dictation!))


(comment
  (ensure-whisper-model!)
  (ensure-whisper-model-async!)

  (start-example-dictation!)
  (stop-example-dictation!)
  ;;
  )
