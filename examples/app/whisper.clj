(ns examples.app.whisper
  (:require [babashka.fs :as fs]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [com.phronemophobic.grease.ios :as ios]
            [com.phronemophobic.grease.ios.webview :as webview]
            [com.phronemophobic.grease.ios.whisper :as whisper])
  (:import [java.net URL]
           [java.lang InterruptedException]
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

(defonce web-dictation-state
  (atom {:handle nil
         :meter-future nil
         :state "idle"}))

(defn- browser-json-value [x]
  (cond
    (nil? x) nil
    (or (string? x)
        (number? x)
        (true? x)
        (false? x)) x
    (keyword? x) (name x)
    (map? x) (into {}
                   (map (fn [[k v]]
                          [(if (keyword? k) (name k) (str k))
                           (browser-json-value v)]))
                   x)
    (sequential? x) (mapv browser-json-value x)
    :else (str x)))

(defn- event-js [event]
  (str "window.dispatchEvent(new CustomEvent('grease:whisper',{detail:"
       (json/write-str (browser-json-value event))
       "}));"))

(defn- dispatch-web-event! [handle event]
  (webview/eval-js! handle (event-js (assoc event :version 1))))

(defn- set-web-state! [state]
  (swap! web-dictation-state assoc :state state))

(defn- stop-meter-loop! []
  (when-let [fut (:meter-future @web-dictation-state)]
    (future-cancel fut))
  (swap! web-dictation-state assoc :meter-future nil))

(defn- report-meter-error? [e]
  (and (not (instance? InterruptedException e))
       (= "listening" (:state @web-dictation-state))))

(defn- start-meter-loop! [handle]
  (stop-meter-loop!)
  (let [fut (future
              (try
                (loop []
                  (when-not (webview/closed? handle)
                    (let [status (whisper/dictation-status)
                          event (merge {:type "meter"}
                                       status)]
                      (dispatch-web-event! handle event)
                      (when (true? (get status "recording"))
                        (ios/sleep 50)
                        (recur)))))
                (catch Exception e
                  (when (and (report-meter-error? e)
                             (not (webview/closed? handle)))
                    (dispatch-web-event! handle {:type "error"
                                                 :state "error"
                                                 :message (or (ex-message e)
                                                              (str e))})))))]
    (swap! web-dictation-state assoc :meter-future fut)
    nil))

(defn- start-web-dictation! [handle]
  (if (not= "idle" (:state @web-dictation-state))
    {:accepted false
     :state (:state @web-dictation-state)}
    (do
      (set-web-state! "preparing")
      (dispatch-web-event! handle {:type "state"
                                   :state "preparing"})
      (future
        (try
          (let [progress! (fn [progress]
                            (dispatch-web-event! handle
                                                 (assoc progress
                                                        :type "download"
                                                        :state "preparing")))
                model-path (ensure-whisper-model! progress!)]
            (when (= "preparing" (:state @web-dictation-state))
              (whisper/start-dictation! {:model-path model-path})
              (set-web-state! "listening")
              (dispatch-web-event! handle {:type "state"
                                           :state "listening"})
              (start-meter-loop! handle)))
          (catch Exception e
            (stop-meter-loop!)
            (set-web-state! "idle")
            (dispatch-web-event! handle {:type "error"
                                         :state "error"
                                         :message (or (ex-message e)
                                                      (str e))}))))
      {:accepted true
       :state "preparing"})))

(defn- stop-web-dictation! [handle]
  (let [state (:state @web-dictation-state)]
    (if (not= "listening" state)
      {:accepted false
       :state state}
      (do
        (stop-meter-loop!)
        (set-web-state! "transcribing")
        (dispatch-web-event! handle {:type "state"
                                     :state "transcribing"})
        (future
          (try
            (let [transcript (whisper/stop-dictation!)]
              (set-web-state! "idle")
              (dispatch-web-event! handle {:type "result"
                                           :state "idle"
                                           :transcript transcript}))
            (catch Exception e
              (set-web-state! "idle")
              (dispatch-web-event! handle {:type "error"
                                           :state "error"
                                           :message (or (ex-message e)
                                                        (str e))}))))
        {:accepted true
         :state "transcribing"}))))

(defn- cancel-web-dictation! [handle]
  (if (= "transcribing" (:state @web-dictation-state))
    {:accepted false
     :state "transcribing"}
    (do
      (stop-meter-loop!)
      (set-web-state! "idle")
      (future
        (try
          (whisper/cancel-dictation!)
          (catch Exception _))
        (dispatch-web-event! handle {:type "state"
                                     :state "idle"}))
      {:accepted true
       :state "idle"})))

(def web-dictation-html
  "<!doctype html>
<html>
<head>
<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">
<style>
html,body{margin:0;height:100%;font:16px -apple-system,BlinkMacSystemFont,sans-serif;background:#101113;color:#f5f5f5}
main{height:100%;display:grid;grid-template-rows:auto 1fr auto;gap:18px;padding:24px;box-sizing:border-box}
.status{font-size:18px;font-weight:600}
.meter{display:flex;align-items:center;justify-content:center;gap:6px;min-height:220px}
.bar{width:10px;height:12px;border-radius:5px;background:#33d17a;transition:height 80ms linear,opacity 120ms linear;opacity:.45}
.controls{display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px}
button{appearance:none;border:0;border-radius:8px;padding:14px 10px;font:inherit;font-weight:700;background:#e8e8e8;color:#111}
button.primary{background:#33d17a}
pre{white-space:pre-wrap;min-height:90px;margin:0;color:#d0d0d0}
</style>
</head>
<body>
<main>
  <div class=\"status\" id=\"status\">Idle</div>
  <div class=\"meter\" id=\"meter\"></div>
  <pre id=\"transcript\"></pre>
  <div class=\"controls\">
    <button class=\"primary\" id=\"start\">Start</button>
    <button id=\"stop\">Stop</button>
    <button id=\"cancel\">Cancel</button>
  </div>
</main>
<script>
const meter = document.getElementById('meter');
const statusEl = document.getElementById('status');
const transcriptEl = document.getElementById('transcript');
const bars = Array.from({length: 24}, () => {
  const bar = document.createElement('div');
  bar.className = 'bar';
  meter.appendChild(bar);
  return bar;
});
let levels = bars.map(() => 0);
function setStatus(text){ statusEl.textContent = text; }
function render(level){
  levels.push(Math.max(0, Math.min(1, Number(level) || 0)));
  levels = levels.slice(-bars.length);
  levels.forEach((value, index) => {
    bars[index].style.height = (12 + value * 190) + 'px';
    bars[index].style.opacity = String(.35 + value * .65);
  });
}
window.addEventListener('grease:whisper', (event) => {
  const detail = event.detail || {};
  if (detail.type === 'meter') {
    setStatus('Listening');
    render(detail.level);
  } else if (detail.type === 'download') {
    setStatus('Downloading ' + (detail.label || 'model') + ' ' + detail.downloaded);
  } else if (detail.type === 'state') {
    setStatus((detail.state || 'idle').replace(/^./, c => c.toUpperCase()));
    if (detail.state === 'idle') render(0);
  } else if (detail.type === 'result') {
    setStatus('Idle');
    transcriptEl.textContent = detail.transcript || '';
    render(0);
  } else if (detail.type === 'error') {
    setStatus('Error');
    transcriptEl.textContent = detail.message || 'Unknown error';
    render(0);
  }
});
document.getElementById('start').onclick = () => Grease.dictation.start();
document.getElementById('stop').onclick = () => Grease.dictation.stop();
document.getElementById('cancel').onclick = () => Grease.dictation.cancel();
render(0);
</script>
</body>
</html>")

(defn open-web-dictation-example! []
  (let [handle* (atom nil)
        handle (webview/open! {:url "https://grease.local"
                               :functions {"dictation"
                                           {"start" (fn []
                                                      (start-web-dictation! @handle*))
                                            "stop" (fn []
                                                     (stop-web-dictation! @handle*))
                                            "cancel" (fn []
                                                       (cancel-web-dictation! @handle*))
                                            "status" (fn []
                                                       (whisper/dictation-status))}}})]
    (reset! handle* handle)
    (swap! web-dictation-state assoc :handle handle)
    (webview/load-html! handle web-dictation-html "https://grease.local/")
    handle))


(defn -main []
  (future (open-web-dictation-example!)))

(comment
  (ensure-whisper-model!)
  (ensure-whisper-model-async!)

  (start-example-dictation!)
  (stop-example-dictation!)
  (open-web-dictation-example!)
  ;;
  )
