(ns pod
  (:require [membrane.basic-components :as basic]
            [org.httpkit.client :as http]
            [membrane.component
             :refer [defui defeffect]]
            [membrane.skia.paragraph :as para]
            [babashka.fs :as fs]
            [clojure.java.io :as io]
            [tech.v3.datatype.ffi :as dt-ffi ]
            [com.phronemophobic.clj-libffi :as ffi]
            [membrane.ui :as ui]
            [tech.v3.datatype.struct :as dt-struct]
            [tech.v3.datatype.native-buffer :as native-buffer]
            [com.phronemophobic.grease.ios :as ios]
            [com.phronemophobic.objcjure :refer [objc describe]
             :as objc]
            [com.phronemophobic.grease.component :as gcomp]
            [clojure.core.async :as async ]
            [clojure.data.json :as json]
            [clojure.data.xml :as xml]
            [clojure.string :as str]
            [clojure.zip :as z]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            app)
  (:import java.security.MessageDigest
           java.time.ZonedDateTime
           java.util.Locale
           java.time.format.DateTimeFormatter))

;; Declare UI state

(defonce pod-state (atom {}))
(defonce handler (membrane.component/default-handler pod-state))

;; Generic config and helpers

(dt-struct/define-datatype!
  :cm_time
  [{:name :value :datatype :int64}
   {:name :timescale :datatype :int32}
   {:name :flags :datatype :uint32}
   {:name :epoch :datatype :int64}])

(dt-struct/define-datatype!
  :cm_timerange
  [{:name :start :datatype :cm_time}
   {:name :duration :datatype :cm_time}])


(def formatter-rfc1123 DateTimeFormatter/RFC_1123_DATE_TIME)
(defn parse-rfc1123 [s]
  (let [zonedDateTime (ZonedDateTime/parse s formatter-rfc1123)]
    (.toInstant zonedDateTime)))

(def NSEC_PER_SEC (int 1000000000))
(defn cm-time-interval [seconds]
  (ffi/call "CMTimeMakeWithSeconds"
            :cm_time
            :float64
            (double seconds)
            :int32
            NSEC_PER_SEC))

(defn path->nsurl [path directory?]
  (assert (string? path))
  (objc [NSURL :fileURLWithPath:isDirectory
         ~(objc/str->nsstring path)
         (byte (if directory?
                 1
                 0))]))

(defn nsmerge [nsdict m]
  (doseq [[k v] m]
    (objc [nsdict :setObject:forKey @v @k]))
  nsdict)


(honey.sql/register-clause!
 :merge-into
 (fn [_ table]
   [(str "MERGE INTO " (honey.sql/format-entity table))])
 :values)

(honey.sql/register-fn!
 :greatest-ignore-nulls
 (fn [_ params]
   (let [[sql] (honey.sql/format-expr (into [:greatest]
                                        params))]
     [(str sql " IGNORE NULLS")])))

(defn lookup-nsstring-symbol [s]
  (let [symbol  (ffi/dlsym (ffi/long->pointer (long -2 ))
                           (dt-ffi/string->c s))

        ;; indirect once
        ptr (ffi/long->pointer
             (native-buffer/read-long
              (native-buffer/wrap-address
               (.address symbol)
               8
               nil)))]
    (objc/nsstring->str ptr)))

(def AVURLAssetPreferPreciseDurationAndTimingKey
  (lookup-nsstring-symbol "AVURLAssetPreferPreciseDurationAndTimingKey"))
(def MPNowPlayingInfoCollectionIdentifier
  "The identifier of the collection the Now Playing item belongs to."
  (lookup-nsstring-symbol "MPNowPlayingInfoCollectionIdentifier"))
(def MPNowPlayingInfoPropertyAdTimeRanges
  "A list of ad breaks in the Now Playing item."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyAdTimeRanges"))
(def MPNowPlayingInfoPropertyAvailableLanguageOptions
  "The available language option groups for the Now Playing item."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyAvailableLanguageOptions"))
(def MPNowPlayingInfoPropertyAssetURL
  "The URL pointing to the Now Playing item’s underlying asset."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyAssetURL"))
(def MPNowPlayingInfoPropertyChapterCount
  "The total number of chapters in the Now Playing item."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyChapterCount"))
(def MPNowPlayingInfoPropertyChapterNumber
  "The number corresponding to the currently playing chapter."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyChapterNumber"))
(def MPNowPlayingInfoPropertyCreditsStartTime
  "The start time for the credits, in seconds, without ads, for the Now Playing item."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyCreditsStartTime"))
(def MPNowPlayingInfoPropertyCurrentLanguageOptions
  "The currently active language options for the Now Playing item."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyCurrentLanguageOptions"))
(def MPNowPlayingInfoPropertyCurrentPlaybackDate
  "The date associated with the current elapsed playback time."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyCurrentPlaybackDate"))
(def MPNowPlayingInfoPropertyDefaultPlaybackRate
  "The default playback rate for the Now Playing item."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyDefaultPlaybackRate"))
(def MPNowPlayingInfoPropertyElapsedPlaybackTime
  "The elapsed time of the Now Playing item, in seconds."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyElapsedPlaybackTime"))
#_(def MPNowPlayingInfoPropertyExcludeFromSuggestions
    "A number that denotes whether to exclude the Now Playing item from content suggestions."
    (lookup-nsstring-symbol "MPNowPlayingInfoPropertyExcludeFromSuggestions"))
(def MPNowPlayingInfoPropertyExternalContentIdentifier
  "The opaque identifier that uniquely identifies the Now Playing item, even through app relaunches."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyExternalContentIdentifier"))
(def MPNowPlayingInfoPropertyExternalUserProfileIdentifier
  "The opaque identifier that uniquely identifies the profile the Now Playing item plays from, even through app relaunches."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyExternalUserProfileIdentifier"))
#_(def MPNowPlayingInfoPropertyInternationalStandardRecordingCode
    "The International Standard Recording Code (ISRC) of the Now Playing item."
    (lookup-nsstring-symbol "MPNowPlayingInfoPropertyInternationalStandardRecordingCode"))
(def MPNowPlayingInfoPropertyIsLiveStream
  "A number that denotes whether the Now Playing item is a live stream."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyIsLiveStream"))
(def MPNowPlayingInfoPropertyMediaType
  "The media type of the Now Playing item."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyMediaType"))
(def MPNowPlayingInfoPropertyPlaybackProgress
  "The current progress of the Now Playing item."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyPlaybackProgress"))
(def MPNowPlayingInfoPropertyPlaybackRate
  "The playback rate of the Now Playing item."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyPlaybackRate"))
(def MPNowPlayingInfoPropertyPlaybackQueueCount
  "The total number of items in the app’s playback queue."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyPlaybackQueueCount"))
(def MPNowPlayingInfoPropertyPlaybackQueueIndex
  "The index of the Now Playing item in the app’s playback queue."
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyPlaybackQueueIndex"))
(def MPNowPlayingInfoPropertyServiceIdentifier
  (lookup-nsstring-symbol "MPNowPlayingInfoPropertyServiceIdentifier"))

(def MPMediaItemPropertyAlbumTitle (lookup-nsstring-symbol "MPMediaItemPropertyAlbumTitle"))
(def MPMediaItemPropertyAlbumTrackCount (lookup-nsstring-symbol "MPMediaItemPropertyAlbumTrackCount"))
(def MPMediaItemPropertyAlbumTrackNumber (lookup-nsstring-symbol "MPMediaItemPropertyAlbumTrackNumber"))
(def MPMediaItemPropertyArtist (lookup-nsstring-symbol "MPMediaItemPropertyArtist"))
(def MPMediaItemPropertyArtwork (lookup-nsstring-symbol "MPMediaItemPropertyArtwork"))
(def MPMediaItemPropertyComposer (lookup-nsstring-symbol "MPMediaItemPropertyComposer"))
(def MPMediaItemPropertyDiscCount (lookup-nsstring-symbol "MPMediaItemPropertyDiscCount"))
(def MPMediaItemPropertyDiscNumber (lookup-nsstring-symbol "MPMediaItemPropertyDiscNumber"))
(def MPMediaItemPropertyGenre (lookup-nsstring-symbol "MPMediaItemPropertyGenre"))
(def MPMediaItemPropertyMediaType (lookup-nsstring-symbol "MPMediaItemPropertyMediaType"))
(def MPMediaItemPropertyPersistentID (lookup-nsstring-symbol "MPMediaItemPropertyPersistentID"))
(def MPMediaItemPropertyPlaybackDuration (lookup-nsstring-symbol "MPMediaItemPropertyPlaybackDuration"))
(def MPMediaItemPropertyTitle (lookup-nsstring-symbol "MPMediaItemPropertyTitle"))

(defn audio-categories []
  (let [audio-session (objc [AVAudioSession sharedInstance])
        categories (objc [audio-session availableCategories])
        num (objc ^long [categories count])]
    (into []
          (map (fn [i]
                 (-> (objc [categories :objectAtIndex i])
                     objc/nsstring->str)))
          (range num))))


(defn audio-modes []
  (let [audio-session (objc [AVAudioSession sharedInstance])
        modes (objc [audio-session availableModes])
        num (objc ^long [modes count])]
    (into []
          (map (fn [i]
                 (-> (objc [modes :objectAtIndex i])
                     objc/nsstring->str)))
          (range num))))

(def AVAudioSessionRouteSharingPolicyLongFormAudio 1)
(def MPRemoteCommandHandlerStatusSuccess 0)
(def MPRemoteCommandHandlerStatusCommandFailed 200)


(defn ->url [url]
  (assert (string? url))
  (objc/arc!
   (objc [NSURL :URLWithString ~(objc/str->nsstring url)])))

(defn documents-dir []
  ;; fileSystemRepresentation
  (io/file
   (dt-ffi/c->string
    (objc [[[[NSFileManager defaultManager] :URLsForDirectory:inDomains
             ;; (int 14) ;; application support
             9 ;; documents
             1
             ]
            :objectAtIndex 0]
           fileSystemRepresentation]))))

(def scripts-dir
  (doto (fs/file  (documents-dir)
                  "scripts")
    fs/create-dirs))

(def episodes-dir
  (doto (fs/file  scripts-dir
                  "episodes")
    fs/create-dirs))

(defn sha256-hex [input]
  (let [digest (MessageDigest/getInstance "SHA-256")
        hash-bytes (.digest digest (.getBytes input "UTF-8"))]
    (apply str (map #(format "%02x" %) hash-bytes))))

(defn episode-file [episode]
  (fs/file episodes-dir
           (str (sha256-hex (:EPISODE/GUID episode))
                ".mp3")))

(defonce log* (atom []))
(defn log [& msgs]
  (swap! log* into msgs)
  nil)

(defn oprn [o]
  (println
   (objc/nsstring->str 
    (objc
     [o :description]))))

;; Podcast Search API

(defn search-podcasts [term]
  (let [response (http/get "https://itunes.apple.com/search"
                           {:query-params
                            {:term term
                             :media "podcast"
                             :limit 10}})]
    (-> (json/read-str (:body @response))
        (get "results"))))

(defn get-episodes [collectionId & {:keys [limit]}]
  (let [response (http/get "https://itunes.apple.com/lookup"
                           {:query-params
                            {:id collectionId
                             :media "podcast"
                             :entity "podcastEpisode"
                             :limit (or limit 200)}})]
    (-> (json/read-str (:body @response))
        (get "results"))))


;; Utilities for parsing rss

(defn zip-iter
  "Returns an eduction of zip nodes given a zipper."
  [zip]
  (eduction
   (take-while #(not (z/end? %)))
   (iterate z/next
            zip)))

(defn find-tag
  "Given an xml zip node, find the next xml element with `tag`."
  [zip tag]
  (some (fn [z]
          (let [node (z/node z)]
            (when (= tag (:tag node))
              node)))
        (zip-iter zip)))

(defn parse-duration
  "Tries to parse the duration. Returns duration in seconds on success or nil on failure."
  [duration]
  (try
    (Integer/parseInt duration)
    (catch Exception e
      (try
        (let [[_ hours minutes seconds]
              (re-matches #"([0-9]*):?([0-9]{2}):([0-9]{2})"
                          duration)
              hours (try (Integer/parseInt hours) (catch Exception e 0))
              minutes (try (Integer/parseInt minutes) (catch Exception e 0))
              seconds (try (Integer/parseInt seconds) (catch Exception e 0))]
          (+ (* hours 3600)
             (* minutes 60)
             seconds))
        (catch Exception e
          nil)))))

(defn parse-item
  "Parse a single item from an rss feed given an xml-zip node for the item."
  [zitem]
  (let [summary (-> (find-tag zitem :xmlns.http%3A%2F%2Fwww.itunes.com%2Fdtds%2Fpodcast-1.0.dtd/summary)
                    :content
                    first)
        title (-> (find-tag zitem :title)
                  :content
                  first)
        url (-> (find-tag zitem :enclosure)
                :attrs
                :url)
        guid (-> (find-tag zitem :guid)
                 :content
                 first
                 str/trim)
        pubDate (-> (find-tag zitem :pubDate)
                    :content
                    first
                    str/trim
                    parse-rfc1123)
        duration (when-let [duration-str (-> (find-tag zitem
                                                       :xmlns.http%3A%2F%2Fwww.itunes.com%2Fdtds%2Fpodcast-1.0.dtd/duration)
                                             :content
                                             first)]
                   (-> duration-str
                       str/trim
                       parse-duration))
        image-url (-> (find-tag zitem
                                :xmlns.http%3A%2F%2Fwww.itunes.com%2Fdtds%2Fpodcast-1.0.dtd/image)
                      :attrs
                      :href)]
    {"description" summary
     "guid" guid
     "trackName" title
     "pubDate" pubDate
     "episodeUrl" url
     "duration" duration
     "imageUrl" image-url}))

(defn parse-rss
  "Downloads the rss feed from `url` and returns a list of parsed episodes."
  [url]
  (let [zrss (with-open [is (io/input-stream (io/as-url url))
                        rdr (io/reader is)]
               (let [xml (xml/parse rdr)
                     zip (z/xml-zip xml)]
                 ;; `xml/parse` returns a lazy data structure
                 ;; walk the whole tree to realize it.
                 (loop [zip zip]
                   (if (z/end? zip)
                     zip
                     (recur (z/next zip))))
                 zip))
        items (into []
                    (comp
                     (filter #(= :item
                                 (:tag (z/node %))))
                     (map parse-item))
                    (zip-iter zrss))]
    items))

;; Database

(def db
  (jdbc/get-datasource
   {:dbtype "h2" :dbname (str (fs/file scripts-dir
                                       "pod-db"))}))

(defn init-db []
  (let [tables [(-> {:create-table [:podcast :if-not-exists]
                     :with-columns
                     [[:collectionId :bigint]
                      [:artistName [:varchar 255]]
                      [:collectionName [:varchar 255]]
                      [:feedUrl [:VARCHAR 255]]
                      [:artworkUrl30 [:VARCHAR 255]]
                      [:artworkUrl100 [:VARCHAR 255]]
                      [:artworkUrl60 [:VARCHAR 255]]
                      [:artworkUrl600 [:VARCHAR 255]]
                      [[:primary-key :collectionId]]]}
                    (sql/format))

                (-> {:create-table [:episode :if-not-exists]
                     :with-columns
                     [[:description [:VARCHAR 1024] ]
                      [:episodeUrl [:VARCHAR 1024]]
                      [:imageUrl [:VARCHAR 1024]]
                      [:guid [:VARCHAR 255]]
                      [:collectionId :bigint]
                      [:duration :bigint]
                      [:trackName [:VARCHAR 255]]
                      [:pubDate :timestamp]
                      [[:foreign-key :COLLECTIONID]
                       [:references :podcast :COLLECTIONID]]
                      [[:primary-key :guid]]]}
                    (sql/format))


                (-> {:create-table [:queue :if-not-exists]
                     :with-columns
                     [[:episodeGuid [:varchar 255]]
                      [:timestamp :double-precision]
                      [:last-played :timestamp]
                      
                      [[:primary-key :episodeGuid]]
                      [[:foreign-key :episodeGuid]
                       [:references :episode :guid]]]}
                    (sql/format))]]
    (doseq [statement tables]
      (jdbc/execute! db statement))))

(defn uppercase-keys [m]
  (reduce-kv (fn [m k v]
               (assoc m (str/upper-case (name k)) v))
             {}
             m))

(defn truncate [s n]
  (if (>= (count s) n)
    (subs s 0 n)
    s))

(defn truncate-description [m]
  (update m "description" #(truncate % 255)))

(defn parse-release-date [m]
  (update m "releaseDate" clojure.instant/read-instant-date))

(defn add-episodes [episodes]
  (sql/format {:merge-into :episode
               :values
               (into []
                     (map (fn [episode]
                            (-> episode
                                (select-keys
                                 ["description" "guid" "trackName" "pubDate" "episodeUrl" "duration" "imageUrl" "collectionId"])
                                truncate-description
                                uppercase-keys)))
                     episodes)}))

(defn add-podcast [podcast episodes]
  [(sql/format {:insert-into :podcast
                :values
                [(-> podcast
                     (select-keys ["collectionId"
                                   "artistName"
                                   "collectionName"
                                   "feedUrl"
                                   "artworkUrl30"
                                   "artworkUrl100"
                                   "artworkUrl60"
                                   "artworkUrl600"])
                     uppercase-keys)]})
   (add-episodes episodes)])

(defn update-podcast-episodes! []
  (let [pods (jdbc/execute! db (sql/format
                                {:select [:FEEDURL :COLLECTIONID]
                                 :from :podcast}))]
    (doseq [pod pods]
      (let [collectionId (:PODCAST/COLLECTIONID pod)
            episodes (into []
                           (map #(assoc % "collectionId" collectionId))
                           (parse-rss (:PODCAST/FEEDURL pod)))]
        (jdbc/execute! db (add-episodes episodes))))))

(defn add-podcast!
  ([podcast]
   (let [episodes (parse-rss (get podcast "feedUrl"))
         {:strs [collectionId]} podcast
         episodes (into []
                        (map #(assoc % "collectionId" collectionId))
                        episodes)]
     (add-podcast! podcast episodes)))
  ([podcast episodes]
   (doseq [statement (add-podcast podcast episodes)]
     (try
       (jdbc/execute! db statement)
       (catch Exception e
         (when-not (str/includes? (ex-message e)
                                  "Unique index or primary key violation")
           (throw e)))))))


(defn remove-podcast! [collection-id]
  (jdbc/execute! db
                 ["delete from `queue` where episodeGuid in (select guid from episode where collectionId=?)" collection-id])
  (jdbc/execute! db
                 (sql/format
                  {:delete-from [:episode]
                   :where [:= :collectionId collection-id]}))

  (jdbc/execute! db
                 (sql/format
                  {:delete-from [:podcast]
                   :where [:= :collectionId collection-id]})))



(defn latest-episodes []
  (with-open [conn (jdbc/get-connection db)]
   (let [search-text (:search-text @pod-state)
         query {:select [:episode/*
                         :queue/last_played
                         :queue/TIMESTAMP]
                :from [:episode]
                :left-join [:queue [:and
                                    [:= :queue/episodeGuid :episode/guid]]]
                :order-by [[[:greatest-ignore-nulls :queue/last_played :episode/pubDate]
                              :desc]]
                :limit 50}
         query (if (seq search-text)
                 (assoc query :where [:like [:lower :episode/TRACKNAME] (str "%" search-text "%")])
                 query)]
     (with-open [conn (jdbc/get-connection db)]
       (jdbc/execute! conn (sql/format query))))))



(comment
  (def podcasts (search-podcasts "defn"))
  (def episodes (get-episodes 1114899563))
  (add-podcast! (first podcasts) )

  (fs/list-dir scripts-dir)
  (fs/delete (fs/file scripts-dir
                      "pod-db.mv.db"))
  (init-db)

  ,)


;; To simplify threading, we serialize all operations
;; that interact with AVPlayer
(defonce player-ch (async/chan))

(defmacro defop [op-name bindings & body]
  (let [op (keyword op-name)
        op-impl-name (symbol (str (name op) "-impl"))]
    `(do
       (defn ~op-impl-name ~bindings
         ~@body)
       (defn ~op-name ~bindings
         (async/put! player-ch {:op ~op
                                :f ~op-impl-name
                                :args ~bindings}))
       ))
  )

(defonce running? (atom false))
(defonce player-thread
  (async/thread
    (reset! running? true)
    (try
      (loop []
        (when-let [msg (async/<!! player-ch)]
          (try
            (apply (:f msg) (:args msg))
            (catch Exception e
              (log [:error e])))
          
          (recur)))
      (finally
        (reset! running? false)))
    (log :quitting))
  ,)


;; Operations for working with AVPlayer

(defn get-player []
  (:player @pod-state))

(def MPMediaTypePodcast 2)
(def MPNowPlayingPlaybackStatePlaying 1)
(def MPNowPlayingPlaybackStatePaused 2)
(defn set-playback-info [playback-state]
  (let [center (objc [MPNowPlayingInfoCenter defaultCenter])

        old-info (objc [center nowPlayingInfo])
        info (if (zero? (.address old-info))
               (objc/arc! (objc [NSMutableDictionary :dictionary]))
               (objc/arc! (objc [NSMutableDictionary :dictionaryWithDictionary old-info])))

        {:keys [current-asset]
         episode :playing-episode} @pod-state]
    (nsmerge info
             (merge
              {MPMediaItemPropertyPlaybackDuration
               ;; in seconds
               (let [item (objc [~(get-player)  :currentItem])
                     duration (objc ^cm_time [item :duration])
                     tv (:value duration)
                     tt (:timescale duration)
                     duration-seconds (if (pos? tt)
                                        (double (/ tv tt))
                                        0.0)]
                 duration-seconds)
               
               MPNowPlayingInfoPropertyDefaultPlaybackRate
               1.25

               MPNowPlayingInfoPropertyElapsedPlaybackTime
               (let [time-passed (objc ^cm_time [~(:player @pod-state) :currentTime])
                     seconds (double (/ (:value time-passed)
                                        (:timescale time-passed)))]
                 seconds)

               MPNowPlayingInfoPropertyPlaybackRate
               1.25

               MPMediaItemPropertyMediaType
               MPMediaTypePodcast}
              (when-let [collectionName (:EPISODE/COLLECTIONNAME episode)]
                {MPMediaItemPropertyArtist
                 collectionName})
              (when-let [trackName  (:EPISODE/TRACKNAME episode)]
                {MPMediaItemPropertyTitle
                 trackName})))


    (objc [center :setNowPlayingInfo info])))

(defop update-queue [guid timestamp]
  ;; don't set queue below 5 seconds.
  (with-open [conn (jdbc/get-connection db)]
    (when (> timestamp 5.0)
      (jdbc/execute!
       conn
       (sql/format
        {:merge-into :queue
         :values
         [{:episodeGuid guid
           :timestamp timestamp
           :last-played (java.util.Date.)}]})))))

(defop play []
  (objc ^void [~(:player @pod-state) :play])
  (set-playback-info MPNowPlayingPlaybackStatePlaying)
  (swap! pod-state assoc :playing? true)

  (when-not (:player-observer @pod-state)
    (let [           observer
          (objc/arc!
           ;; - (id)addPeriodicTimeObserverForInterval:(CMTime)interval 
           ;;                                  queue:(dispatch_queue_t)queue 
           ;;                             usingBlock:(void (^)(CMTime time))block;
           (objc [~(:player @pod-state)
                  :addPeriodicTimeObserverForInterval:queue:usingBlock
                  ~(cm-time-interval 5)
                  nil
                  (fn ^void [^cm_time time]
                    (let [{:EPISODE/keys [GUID]} (:playing-episode @pod-state)]
                      (update-queue GUID (double (/ (:value time)
                                                    (:timescale time))))))]))]
      (swap! pod-state assoc :player-observer observer))))

(defop pause []
  (objc ^void [~(:player @pod-state) :pause])
  (set-playback-info MPNowPlayingPlaybackStatePaused)
  (swap! pod-state assoc :playing? false))

(defop toggle []
  (log "toggling")
  (if (zero? (objc ^float [~(:player @pod-state) :rate]))
    (play)
    (pause)))

(defop seek-to-time [player cm-time]
  (let [p (promise)]
    (objc ^void [player :seekToTime:completionHandler
                 cm-time
                 (fn ^void [^byte finished]
                   (set-playback-info MPNowPlayingPlaybackStatePlaying)
                   (deliver p true))])
    @p))

(defop skip-forward [interval]
  (let [player (get-player)
        current-time (objc ^cm_time [~(:player @pod-state) :currentTime])
        tv (:value current-time)
        tt (:timescale current-time)
        duration-seconds (if (pos? tt)
                           (double (/ tv tt))
                           0.0)]
    (.put current-time :value (+ tv (long (* interval tt))))
    (seek-to-time-impl player current-time))
  )

(defop skip-backward [interval]
  (let [player (get-player)
        current-time (objc ^cm_time [~(:player @pod-state) :currentTime])
        tv (:value current-time)
        tt (:timescale current-time)]
    (.put current-time :value (max 0 (- tv (long (* interval tt)))))
    (seek-to-time-impl player current-time)))

(defn download-episode
  "Downloads episode if not already downloaded"
  [episode on-status]
  (let [f (episode-file episode)
        url (io/as-url (:EPISODE/EPISODEURL episode))]
    (log [:downloading (not (fs/exists? f))])
    (when (not (fs/exists? f))
      (try
        (on-status "downloading...")
        (with-open [is (io/input-stream url)]
          (io/copy is
                   f))
        (on-status "done!")
        (catch Exception e
          (on-status(str "failed: " e))
          (throw e))))))

(defop delete-episode [episode on-status]
  (let [f (episode-file episode)
        success? (fs/delete-if-exists f)]
    (on-status (str "Deleted: " (if success? "true" "false")))))


(defop load-episode [episode on-status]
  (let [change? (not= (:playing-episode @pod-state)
                      episode)]
    (log [:load-episode change?])
    (when change?
      (let [_ (download-episode episode on-status)
            
            pod-url (path->nsurl
                     (str (episode-file episode))
                     false)
            
            asset (objc [AVURLAsset :URLAssetWithURL:options pod-url
                         @{@AVURLAssetPreferPreciseDurationAndTimingKey
                           [NSNumber :numberWithBool ~(byte 1)]}])

            player (get-player)
            player-item (objc/arc! (objc [AVPlayerItem :playerItemWithAsset asset] ))]
        (swap! pod-state assoc :playing-episode episode)
        (objc [player :replaceCurrentItemWithPlayerItem player-item])
        (let [{:EPISODE/keys [GUID]} episode
              row (jdbc/execute-one! db
                                     (sql/format
                                      {:select [:TIMESTAMP]
                                       :from [:queue]
                                       :where [:and
                                               [:= :EPISODEGUID GUID]]}))
              timestamp (:QUEUE/TIMESTAMP row)]
          (when timestamp
            (log [:starting-at timestamp])
            (seek-to-time player (cm-time-interval timestamp))))))))

(defn configure-audio []
  (let [p (promise)]
    
    (not (zero?
          (objc ^long [[AVAudioSession sharedInstance] :setCategory:mode:routeSharingPolicy:options:error
                       @"AVAudioSessionCategoryPlayback"
                       @"AVAudioSessionModeDefault"
                       AVAudioSessionRouteSharingPolicyLongFormAudio
                       ~(long 0)
                       nil])))
    (objc ^void
          [[AVAudioSession :sharedInstance]
           :activateWithOptions:completionHandler
           ~(long 0)
           (fn ^void [^byte activated error]
             (deliver p (not (zero? activated))))])
    @p))

(defn configure-controls
  "Add handlers for buttons on the lock screen."
  [player]
  (let [commandCenter (objc [MPRemoteCommandCenter :sharedCommandCenter])]
    (objc
     [[commandCenter :playCommand]
      :addTargetWithHandler
      (fn ^long [event]
        (if (zero? (objc ^float [player rate]))
          (do
            (play)
            MPRemoteCommandHandlerStatusSuccess)
          ;; else
          MPRemoteCommandHandlerStatusCommandFailed))])

    (objc
     [[commandCenter :changePlaybackPositionCommand]
      :addTargetWithHandler
      (fn ^long [event]
        (let [position (objc ^double [event :positionTime])]
          (log :changePlaybackPositionCommand
               position)
          (seek-to-time (get-player) (cm-time-interval position)))
        
        MPRemoteCommandHandlerStatusCommandFailed)])
    (objc
     [[commandCenter :stopCommand]
      :addTargetWithHandler
      (fn ^long [event]
        (if (pos? (objc ^float [player rate]))
          (do (pause)
              MPRemoteCommandHandlerStatusSuccess)
          (do
            MPRemoteCommandHandlerStatusCommandFailed)))])
    (objc
     [[commandCenter :togglePlayPauseCommand]
      :addTargetWithHandler
      (fn ^long [event]
        (toggle)
        MPRemoteCommandHandlerStatusSuccess)])

    (objc
     [[commandCenter :skipForwardCommand]
      :addTargetWithHandler
      (fn ^long [event]
        (let [interval (objc ^double [event :interval])]
          (skip-forward interval))
        MPRemoteCommandHandlerStatusSuccess)])
    ;; also use next track command to skip
    ;; so that it can be used in the car.
    (objc
     [[commandCenter :nextTrackCommand]
      :addTargetWithHandler
      (fn ^long [event]
        (skip-forward 30)
        MPRemoteCommandHandlerStatusSuccess)])
    (objc
     [[commandCenter :skipBackwardCommand]
      :addTargetWithHandler
      (fn ^long [event]
        (let [interval (objc ^double [event :interval])]
          (skip-backward interval))
        MPRemoteCommandHandlerStatusSuccess)])
    ;; also use next track command to skip
    ;; so that it can be used in the car.
    (objc
     [[commandCenter :previousTrackCommand]
      :addTargetWithHandler
      (fn ^long [event]
        (skip-backward 5)
        MPRemoteCommandHandlerStatusSuccess)])))

(let [__init
      (delay
        (init-db)
        (configure-audio)
        
        (let [player (objc/arc!
                      (objc [[[AVPlayer :alloc] :init] :autorelease]))]
          (objc ^void [player :setDefaultRate ~(float 1.25)])
          
          (swap! pod-state assoc
                 :view :main
                 :player player)
          (configure-controls player))

        (handler ::refresh-episodes {}))]
 (defop init []
   @__init))

;; UI

(defeffect ::load-episode [{:keys [episode $status]}]
  (dispatch! :set $status "")
  (load-episode episode
                #(dispatch! :set $status %)))
(defeffect ::toggle []
  (toggle))
(defeffect ::play []
  (play))
(defeffect ::pause []
  (pause))
(defeffect ::skip-forward []
  (skip-forward 30))
(defeffect ::skip-backward []
  (skip-backward 5))
(defeffect ::delete-episode [{:keys [episode $status]}]
  (dispatch! :set $status "")
  (delete-episode episode #(dispatch! :set $status %)))
(defeffect ::download-episode [{:keys [episode $status]}]
  (dispatch! :set $status "")
  (async/put! player-ch
              {:op ::download-episode
               :f download-episode
               :args [episode #(dispatch! :set $status %)]}))

(defeffect ::refresh-episodes [{}]
  (future
    (swap! pod-state assoc :episodes (latest-episodes))))

(defn button [text on-click]
  (ui/on
   :mouse-down
   (fn [_]
     (when on-click
       (on-click)))
   (ui/bordered
    [20 20]
    (ui/label text (ui/font nil 33)))))

(defn format-seconds [seconds]
  (let [hours (quot seconds
                    3600)
        seconds (- seconds (* hours 3600))
        minutes (quot seconds 60)
        seconds (- seconds (* minutes 60))]
    (cond
      (pos? hours) (format
                    "%d:%02d:%02d" (long hours) (long minutes) (long seconds))
      (pos? minutes) (format
                    "%02d:%02d"  (long minutes) (long seconds))
      :else (format
             "%02ds" (long seconds)))))
(defn format-millis [millis]
  (format-seconds (/ millis 1000)))

(defn progress-bar [progress w h]
  (let [progress (max 0
                      (min 1.0
                           progress))]
    [(ui/filled-rectangle [0.7 0.7 0.7]
                          w h)
     (ui/filled-rectangle [0 1 0.2 0.4]
                          (* progress w) h)]))

(defui episode-viewer [{:keys [page episodes search-text]}]
  (ui/vertical-layout
   (button "refresh"
           (fn []
             [[::refresh-episodes {}]]))
   (let [search-text (or search-text "")]
     (ui/padding 5
                 (ui/horizontal-layout
                  (ui/label "search: ")
                  (basic/textarea {:text search-text}))))
   (gcomp/scrollview
    {:scroll-bounds [300 500]
     :extra (get extra ::scrollveiw)
     :$body nil
     :body
     (apply ui/vertical-layout
            (for [episode (take 50 episodes)]
              (ui/on
               :mouse-down
               (fn [_]
                 [[::select-episode {:episode episode}]])
               (when-let [duration (or (:EPISODE/DURATION episode)
                                       (* 60 60))]
                 (ui/bordered [5 20]
                              (ui/vertical-layout
                               (ui/label (:EPISODE/TRACKNAME episode))
                               (ui/label
                                (str
                                 (when-let [ts (:QUEUE/TIMESTAMP episode)]
                                   (str (format-seconds ts) " / "))
                                 (format-seconds duration)))
                               (when-let [ts (:QUEUE/TIMESTAMP episode)]
                                 (progress-bar (/ ts duration)
                                               200 10))
                               ))))))})))



(membrane.component/defui
 episode-view
 [{:as this, :keys [status width height episode]}]
 (clojure.core/let
  [width-128062 width
   
   height-128063 height
   
   G__128064
   (membrane.ui/translate
    (clojure.core/+ 0 9.23828125)
    (clojure.core/+ 0 10.1484375)
    (com.phronemophobic.membrandt/button
     {:on-click (fn [] [[:pod/back]]), :text "<back"}))
   G__128065
   (membrane.ui/translate
    (clojure.core/+ 0 10.27734375)
    (clojure.core/+ 0 59.81640625)
    (membrane.skia.paragraph/paragraph
     (:EPISODE/TRACKNAME episode)
     width
     {:paragraph-style/text-style
      {:text-style/font-size 14,
       :text-style/height 1.5714285714285714,
       :text-style/height-override true,
       :text-style/color [0.0 0.0 0.0 0.88]}}))
   G__128066
   (membrane.ui/translate
    (clojure.core/+ width-128062 -55.59375)
    (clojure.core/+ height-128063 -41.7265625)
    (com.phronemophobic.membrandt/button
     {:on-click (fn [] [[:pod/skip-forward]]), :text ">>"}))
   G__128069
   (membrane.ui/translate
    (clojure.core/+ (membrane.ui/origin-x G__128065) 2.6875)
    (clojure.core/+
     (clojure.core/+
      (membrane.ui/origin-y G__128065)
      (membrane.ui/height G__128065))
     6.44140625)
    (membrane.skia.paragraph/paragraph
     (str (:EPISODE/PUBDATE episode))
     width
     {:paragraph-style/text-style
      {:text-style/font-size 14,
       :text-style/height 1.5714285714285714,
       :text-style/height-override true,
       :text-style/color [0.0 0.0 0.0 0.88]}}))

   downloaded? (fs/exists? (episode-file episode))
   G__128067
   (membrane.ui/translate
    (clojure.core/+ (membrane.ui/origin-x G__128069) -1.69921875)
    (clojure.core/+
     (clojure.core/+
      (membrane.ui/origin-y G__128069)
      (membrane.ui/height G__128069))
     8.15625)
    (com.phronemophobic.membrandt/button
     {:on-click (fn []
                  (if downloaded?
                    [[::delete-episode {:episode episode :$status $status}]]
                    [[::download-episode {:episode episode
                                          :$status $status}]])),
      :text
      (if downloaded?
        "Delete"
        "Download")}))
   G__128068
   (membrane.ui/translate
    (clojure.core/+ (membrane.ui/origin-x G__128067) 4.99609375)
    (clojure.core/+
     (clojure.core/+
      (membrane.ui/origin-y G__128067)
      (membrane.ui/height G__128067))
     10.484375)
    (membrane.skia.paragraph/paragraph
     (:EPISODE/DESCRIPTION episode)
     width
     {:paragraph-style/text-style
      {:text-style/font-size 14,
       :text-style/height 1.5714285714285714,
       :text-style/height-override true,
       :text-style/color [0.0 0.0 0.0 0.88]}}))
   G__128072
   (membrane.ui/translate
    (clojure.core/+ 0 10.25)
    (clojure.core/+ height-128063 -40.3515625)
    (com.phronemophobic.membrandt/button
     {:on-click (fn [] [[:pod/skip-backward]]), :text "<<"}))
   G__128070
   (membrane.ui/translate
    (clojure.core/+ (membrane.ui/origin-x G__128072) 4.7578125)
    (clojure.core/+ (membrane.ui/origin-y G__128072) -36.3984375)
    (membrane.skia.paragraph/paragraph
     (str status)
     nil
     {:paragraph-style/text-style
      {:text-style/font-size 14,
       :text-style/height 1.5714285714285714,
       :text-style/height-override true,
       :text-style/color [0.0 0.0 0.0 0.88]}}))
   G__128071
   (membrane.ui/translate
    (clojure.core/+ (membrane.ui/origin-x G__128066) -69.734375)
    (clojure.core/+ (membrane.ui/origin-y G__128066) -0.94140625)
    (com.phronemophobic.membrandt/button
     {:on-click
      (fn
       []
        (prn "hi")
        [[:pod/load-episode {:episode episode, :$status $status}]
         [:pod/toggle]]),
      :text "Play"}))]
  [G__128072
   G__128066
   G__128071
   G__128064
   G__128065
   G__128070
   G__128069
   G__128067
   G__128068]))

(defeffect ::search-podcasts [{:keys [$podcasts
                                      query]}]
  (future
    (try
      (let [podcasts (search-podcasts query)]
        (log podcasts)
        (dispatch! :set $podcasts podcasts))
      (catch Exception e
        (log e)))))

(defeffect ::add-podcast [{:keys [podcast $status]}]
  (future
    (try
      (log :adding-podcast)
      (dispatch! :set $status "Adding podcast...")
      (add-podcast! podcast)
      (dispatch! :set $status "")
      (log :done-adding-podcast)
      (catch Exception e
        (dispatch! :set $status "adding podcast failed :(")
        (log e)))))

(defui search-view [{:keys []}]
  (let [search-text (get extra :search-text "")
        status (get extra :status "")
        podcasts (get extra :podcasts [])]
    (apply
     ui/vertical-layout
     (ui/horizontal-layout
      (button "Search"
              (fn []
                [[::search-podcasts {:$podcasts $podcasts
                                     :query search-text}]]))
      (basic/textarea {:text search-text}))
     (ui/label status)
     (for [podcast podcasts]
       (ui/on
        :mouse-down
        (fn [_]
          [[::add-podcast {:podcast podcast
                           :$status $status}]
           [:set $podcasts []]])
        (ui/bordered
         20
         (ui/label (get podcast "collectionName"))))))))

(defeffect ::clear-podcasts [{}]
  (future
    (run!
     fs/delete
     (fs/list-dir episodes-dir))))

(defeffect ::refresh-all [{}]
  (future
    (try
      (update-podcast-episodes!)
      (dispatch! ::refresh-episodes {})
      (ios/prompt-bool {:title "done!"})
      (catch Exception e
        (log e)))))

(defui util-view [{:keys []}]
  (ui/vertical-layout
   (button "clear podcasts"
           (fn []
             [[::clear-podcasts {}]]))
   (button "refresh-all"
           (fn []
             [[::refresh-all {}]]))))

(defui pod-ui [{:keys [playing? episodes selected-episode
                       search-text
                       view]}]
  (ui/translate
   10 50
   (ui/vertical-layout
    (basic/dropdown {:options [[:main "main"]
                               [:search "search "]
                               [:util "util"]]
                     :selected view})
    (case view
      :util (util-view {})

      :search
      (search-view {})
      ;; else
      (if selected-episode
        (ui/on
         ::back
         (fn []
           [[:set $selected-episode nil]])
         (episode-view {:episode selected-episode
                        :width 290
                        :height 550}))
        (ui/on
         ::select-episode
         (fn [{:keys [episode]}]
           [[:set $selected-episode episode]])
         (episode-viewer {:episodes episodes
                          :search-text search-text})))))))

(def app (membrane.component/make-app #'pod-ui pod-state handler))

(defn -main []
  (init)
  (let [{:keys [repaint!]}
        (app/show! {:on-close (fn []
                               (log ::closing)
                                (swap! pod-state dissoc :repaint!))
                    :view-fn app})]
    (swap! pod-state assoc :repaint! repaint!)))

(add-watch pod-state ::update-view
           (fn [k ref old updated]
             (when-let [repaint! (:repaint! updated)]
               (when (not= old updated)
                 (repaint!)))))

(add-watch pod-state ::handle-keyboard
           (fn [k ref old new]
             (let [focus-path [:membrane.component/context :focus]
                   old-focus (get-in old focus-path)
                   new-focus (get-in new focus-path)]
               (when (not= old-focus new-focus)
                 (future
                   (if new-focus
                     (ios/show-keyboard)
                     (ios/hide-keyboard)))))))



