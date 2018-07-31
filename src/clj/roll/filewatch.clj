(ns roll.filewatch
  (:require [clojure.java.io :refer [file]]
            [clojure.set     :refer [rename-keys]]
            [clojure.core.async :as async]
            [com.rpl.specter :as sr :refer [ALL MAP-VALS transform]])
  
  (:import  [java.nio.file Paths FileSystems
             StandardWatchEventKinds]))




#_(defn event-handler [event]
    (println (.kind event) (.context event)))



(defn register-events! [dir watch-service opts]
  (.register dir watch-service
             (-> opts
                 (select-keys [StandardWatchEventKinds/ENTRY_CREATE
                               StandardWatchEventKinds/ENTRY_MODIFY
                               StandardWatchEventKinds/ENTRY_DELETE
                               StandardWatchEventKinds/OVERFLOW])
                 (keys)
                 (into-array))
             (into-array
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
                               (handler path))))
              (watch f)))))))
