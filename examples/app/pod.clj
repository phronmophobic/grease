(ns pod
  (:require [membrane.basic-components :as basic]
            [org.httpkit.client :as http]
            [membrane.component
             :refer [defui defeffect]]
            [babashka.fs :as fs]
            [clojure.java.io :as io]
            [tech.v3.datatype.ffi :as dt-ffi ]
            [com.phronemophobic.clj-libffi :as ffi]
            [membrane.ui :as ui]
            [tech.v3.datatype.struct :as dt-struct]
            [tech.v3.datatype.native-buffer :as native-buffer]
            [com.phronemophobic.grease.objc :as grease]
            [com.phronemophobic.objcjure :refer [objc describe]
             :as objc]
            [clojure.core.async :as async ]
            [clojure.data.json :as json]
            [clojure.data.xml :as xml]
            [clojure.string :as str]
            [clojure.zip :as z]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            ))

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

(defn episode-file [episode]
  (fs/file episodes-dir
           (str (:EPISODE/COLLECTIONID episode)
                "-"
                (:EPISODE/TRACKID episode)
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
                             }})]
    (json/read-str (:body @response))))

(defn get-episodes [collectionId & {:keys [limit]}]
  (let [response (http/get "https://itunes.apple.com/lookup"
                           {:query-params
                            {:id collectionId
                             :media "podcast"
                             :entity "podcastEpisode"
                             :limit (or limit 200)}})]
    (json/read-str (:body @response))))


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
                     [[:description [:varchar 255] ]
                      [:episodeUrl [:VARCHAR 255]]
                      [:episodeGuid [:VARCHAR 255]]
                      [:collectionId :bigint]
                      [:trackId :bigint]
                      [:trackTimeMillis :bigint]
                      [:trackName [:VARCHAR 255]]
                      [:releaseDate :timestamp]
                      [:artistName [:varchar 255]]
                      [:collectionName [:varchar 255]]
                      [[:primary-key :collectionId :trackid]]]}
                    (sql/format))


                (-> {:create-table [:queue :if-not-exists]
                     :with-columns
                     [[:collectionId :bigint]
                      [:trackid :bigint]
                      [:timestamp :double-precision]
                      [:last-played :timestamp]
                      
                      [[:primary-key :collectionId :trackid]]]}
                    (sql/format))]]
    (doseq [statement tables]
      (jdbc/execute! db statement))))

(defn uppercase-keys [m]
  (reduce-kv (fn [m k v]
               (assoc m (str/upper-case k) v))
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

(defn add-podcast [podcast episodes]
  (into [(sql/format {:insert-into :podcast
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
                           uppercase-keys)]})]
        
        (map (fn [episode]
               (sql/format {:insert-into :episode
                            :values
                            [(-> episode
                                 (select-keys
                                  ["description"
                                   "artistName"
                                   "collectionName"
                                   "episodeUrl" 
                                   "episodeGuid" 
                                   "collectionId" 
                                   "trackId"
                                   "trackName"
                                   "releaseDate"
                                   "trackTimeMillis"
                                   "releaseDate"])
                                 truncate-description
                                 uppercase-keys)]})))
        episodes))

(defn add-podcast!
  ([podcast]
   (when-let [collection-id (get podcast "collectionId")]
     (let [episodes (-> (get-episodes collection-id)
                        (get "results")
                        (->> (filter #(get % "episodeUrl"))))]
       (add-podcast! podcast episodes))))
  ([podcast episodes]
   (doseq [statement (add-podcast podcast episodes)]
     (try
       (jdbc/execute! db statement)
       (catch Exception e
         (when-not (str/includes? (ex-message e)
                                  "Unique index or primary key violation")
           (throw e)))))))

(comment
  (def podcasts (search-podcasts "defn"))
  (def episodes (get-episodes 1114899563))
  (add-podcast! (-> podcasts (get "results") first) )

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
          (apply (:f msg) (:args msg))
          
          (recur)))
      (catch Exception e
        (log [:error e]))
      (finally
        (reset! running? false)))
    (log :quitting))
  ,)


;; Declare UI state

(defonce pod-state (atom {}))
(declare repaint!)

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
               1.0

               MPNowPlayingInfoPropertyElapsedPlaybackTime
               (let [time-passed (objc ^cm_time [~(:player @pod-state) :currentTime])
                     seconds (double (/ (:value time-passed)
                                        (:timescale time-passed)))]
                 seconds)

               MPNowPlayingInfoPropertyPlaybackRate
               1.0

               MPMediaItemPropertyMediaType
               MPMediaTypePodcast}
              (when-let [collectionName (:EPISODE/COLLECTIONNAME episode)]
                {MPMediaItemPropertyArtist
                 collectionName})
              (when-let [trackName  (:EPISODE/TRACKNAME episode)]
                {MPMediaItemPropertyTitle
                 trackName})))


    (objc [center :setNowPlayingInfo info])))

(defop update-queue [trackId collectionId timestamp]
  ;; don't set queue below 5 seconds.
  (with-open [conn (jdbc/get-connection db)]
    (when (> timestamp 5.0)
      (jdbc/execute!
       conn
       (sql/format
        {:merge-into :queue
         :values
         [{:trackID trackId
           :collectionId collectionId
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
                    (let [{:EPISODE/keys [TRACKID COLLECTIONID]} (:playing-episode @pod-state)]
                      
                      (update-queue TRACKID COLLECTIONID (double (/ (:value time)
                                                                    (:timescale time)))))
                    )]))]
      (log observer)
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
    @p)
  )

(defop skip-forward [interval]
  (let [player (get-player)
        current-time (objc ^cm_time [~(:player @pod-state) :currentTime])
        tv (:value current-time)
        tt (:timescale current-time)
        duration-seconds (if (pos? tt)
                           (double (/ tv tt))
                           0.0)
        ]
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
  [episode]
  (let [f (episode-file episode)
        url (io/as-url (:EPISODE/EPISODEURL episode))]
    (log [:downloading (not (fs/exists? f))])
    (when (not (fs/exists? f))
      (with-open [is (io/input-stream url)]
        (io/copy is
                 f)))))

(defop load-episode [episode]
  (let [change? (not= (:playing-episode @pod-state)
                      episode)]
    (log [:load-episode change?])
    (when change?
      (let [_ (download-episode episode)
            
            pod-url (path->nsurl
                     (str (episode-file episode))
                     false)
            
            asset (objc [AVURLAsset :URLAssetWithURL:options pod-url
                         @{@AVURLAssetPreferPreciseDurationAndTimingKey
                           [NSNumber :numberWithBool ~(byte 1)]}])

            player (get-player)
            player-item (objc/arc! (objc [AVPlayerItem :playerItemWithAsset asset] ))]
        (objc [player :replaceCurrentItemWithPlayerItem player-item])
        (let [{:EPISODE/keys [TRACKID COLLECTIONID]} episode
              row (jdbc/execute-one! db
                                     (sql/format
                                      {:select [:TIMESTAMP]
                                       :from [:queue]
                                       :where [:and
                                               [:= :TRACKID TRACKID]
                                               [:= :COLLECTIONID COLLECTIONID]
                                               ]}))
              timestamp (:QUEUE/TIMESTAMP row)]
          (when timestamp
            (log [:starting-at timestamp])
            (seek-to-time player (cm-time-interval timestamp))))

        (swap! pod-state assoc :playing-episode episode)))))

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
    (objc
     [[commandCenter :skipBackwardCommand]
      :addTargetWithHandler
      (fn ^long [event]

        (let [interval (objc ^double [event :interval])]
          (skip-backward interval))
        MPRemoteCommandHandlerStatusSuccess)])))

(defop init []
  (configure-audio)
  
  (let [player (objc/arc!
                (objc [[[AVPlayer :alloc] :init] :autorelease]))]

    (swap! pod-state assoc
           :player player)
    (configure-controls player))

  (with-open [conn (jdbc/get-connection db)]
    (swap! pod-state assoc :episodes (jdbc/execute! conn ["select * from episode order by RELEASEDATE desc limit 10"])))
  (repaint!)
  ,)

;; UI

(defeffect ::load-episode [{:keys [episode]}]
  (load-episode episode))
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

(defui episode-viewer [{:keys [page episodes]}]
  (apply ui/vertical-layout
         (for [episode (take 10 episodes)]
           (ui/on
            :mouse-down
            (fn [_]
              [[::select-episode {:episode episode}]])
            (ui/bordered [5 20]
                         (ui/label (:EPISODE/TRACKNAME episode)))))))

(defn button [text on-click]
  (ui/on
   :mouse-down
   (fn [_]
     (when on-click
       (on-click)))
   (ui/bordered
    [20 20]
    (ui/label text (ui/font nil 33)))))

(defui episode-view [{:keys [episode]}]
  (ui/vertical-layout
   (button "<back"
           (fn []
             [[::back]]))
   (button ">>"
           (fn []
             [[::skip-forward]]))
   (button "<<"
           (fn []
             [[::skip-backward]]))
   (ui/label (:EPISODE/TRACKNAME episode))
   (ui/label (:EPISODE/DESCRIPTION episode))
   (button "play"
           (fn []
             [[::load-episode {:episode episode}]
              [::toggle]]))))

(defui pod-ui [{:keys [playing? episodes selected-episode]}]
  (ui/translate
   10 50
   (if selected-episode
     (ui/on
      ::back
      (fn []
        [[:set $selected-episode nil]])
      (episode-view {:episode selected-episode}))
     (ui/on
      ::select-episode
      (fn [{:keys [episode]}]
        [[:set $selected-episode episode]])
      (episode-viewer {:episodes episodes})))))

(def app (membrane.component/make-app #'pod-ui pod-state))

(defn repaint! []
  (reset! main-view (app)))

(defonce __initialize
  (init))

(add-watch pod-state ::update-view
           (fn [k ref old updated]
             (repaint!)))

