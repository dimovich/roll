(ns roll.filewatch
  (:require [taoensso.timbre :refer [info]]
            [clojure.java.io :refer [file]]
            [clojure.set     :refer [rename-keys]]
            [integrant.core  :as ig]
            [clojure.core.async :as async]
            [com.rpl.specter :as sr :refer [transform ALL MAP-VALS]]
            [roll.util :refer [resolve-map-vals]])
  
  (:import  [java.nio.file Paths FileSystems
             StandardWatchEventKinds]))




#_(defn event-handler [file-path] ...)



(defn register-events! [dir watch-service opts]
  (.register dir watch-service
             (-> opts
                 (select-keys [StandardWatchEventKinds/ENTRY_CREATE
                               StandardWatchEventKinds/ENTRY_MODIFY
                               StandardWatchEventKinds/ENTRY_DELETE
                               StandardWatchEventKinds/OVERFLOW])
                 (keys)
                 (into-array))
             #_(into-array
                [(com.sun.nio.file.SensitivityWatchEventModifier/HIGH)])))



(defn rename-event-keys [opts]
  (rename-keys opts
               {:create StandardWatchEventKinds/ENTRY_CREATE
                :modify StandardWatchEventKinds/ENTRY_MODIFY
                :delete StandardWatchEventKinds/ENTRY_DELETE
                :overflow StandardWatchEventKinds/OVERFLOW}))



(defn watch-loop [watch-service opts]
  (async/go-loop []
    (let [k (.take watch-service)]
      (doseq [event (.pollEvents k)]
        (when-let [handler (get opts (.kind event))]
          ;; fixme: call handler when file readable
          (handler event)))
      (when (.reset k) (recur)))))



(defn watch [f opts]
  (let [parent (or (-> f (.getParent)) ".")
        dir  (->  parent (file) (.toURI) (Paths/get))
        opts (rename-event-keys opts)
        watch-service (.newWatchService (FileSystems/getDefault))
        watch-key (register-events! dir watch-service opts)]
    (watch-loop watch-service opts)
    watch-key))



;; {:k WatchKey}
(def state (atom {}))


(defn stop-watch! [k]
  (swap! state update k #(some-> % (.cancel))))


(defn start-watch! [k path opts]
  (let [f (file path)
        opts (if (fn? opts) {:modify opts} opts)]
    
    (when (.isFile f)
      ;; stop existing key handler
      (when (get @state k)
        (stop-watch! k))
      
      (let [fname (.getName f)]
        (swap!
         state assoc k
         (->> opts
              ;; run handler only when this file is changed
              (transform
               [MAP-VALS] (fn [handler]
                            #(when (= fname (.toString (.context %)))
                               (info "modified:" path)
                               (handler path))))
              (watch f)))))))




(defmethod ig/init-key :data/file [_ {:keys [path init watch] :as opts}]
  (let [{:as opts :keys [init watch]} (resolve-map-vals opts)]
    (info "data file: " opts)
    
    (when init
      (init path))

    (when-let [watch (if (true? watch) init watch)]
      (start-watch! path path watch)
      ;; return stop-fn
      #(stop-watch! path))))



(defmethod ig/halt-key! :data/file [_ stop-fn]
  (when stop-fn
    (info "stopping data file watch...")
    (stop-fn)))

