(ns roll.core
  (:require [taoensso.timbre :as timbre :refer [info]]
            [taoensso.timbre.appenders.core :as appenders]
            [integrant.core :as ig]))



(defonce state (atom nil))

(derive :roll/httpkit :roll/server)
(derive :roll/nginx   :roll/server)
(derive :roll/aleph   :roll/server)



(def default-appenders
  {:println (timbre/println-appender {:stream :auto})
   :spit    (appenders/spit-appender {:fname  "timbre.log"})})



(defmethod ig/init-key :roll/timbre [_ {:keys [appenders]}]
  (when appenders
    (info "timbre appenders:" appenders)
    (timbre/merge-config!
     {:appenders (->> appenders
                      (select-keys default-appenders)
                      (merge (zipmap (keys default-appenders)
                                     (repeat nil))))})))




(defn load-configs [configs]
  (let [configs (cond-> configs
                  (not (sequential? configs)) vector)]
    (reduce
     (fn [all config]
       (if-let [ig-config
                (cond
                  (string? config) (ig/read-string (slurp config))
                  (map? config)    config
                  :default nil)]
         (merge all ig-config)
         all))
     {}
     configs)))



(defn halt! []
  (when-let [roll-state (:roll @state)]
    (info "shutting down...")
    (ig/halt! roll-state)))




(defn init [configs]
  (timbre/set-config!
   {:level :info
    :output-fn (fn [{:keys [timestamp_ level msg_]}] (force msg_))
    :appenders (select-keys default-appenders [:println])})

  
  (let [ig-config (load-configs configs)]
    
    (swap! state update :shutdown-hook
           (fn [sh]
             (or sh (do (.addShutdownHook
                         (Runtime/getRuntime) (Thread. halt!))
                        true))))
    

    ;; ensure we have Sente for :roll/reload
    (let [ig-config (cond-> ig-config
                      (:roll/reload ig-config)
                      (update-in [:roll/handler :sente]
                                 (fnil identity true)))]
      

      (halt!) ;;stop current services
      
      (ig/load-namespaces ig-config)
      (swap! state assoc :config ig-config)
      
      (->> (ig/init ig-config)
           (swap! state assoc :roll)))))




(defn restart [& ks]
  (ig/halt! (:roll @state) ks)
  (->> (ig/init (:config @state) ks)
       (swap! state update :roll merge)))




(defn reload [paths]
  (let [new-config (load-configs paths)
        old-config (:config @state)
        dependents (->> (ig/dependency-graph old-config)
                        :dependents)
        changed (->> new-config
                     (reduce-kv
                      (fn [changed k v]
                        (cond-> changed
                          (not= v (get old-config k))
                          (assoc k v)))
                      {}))
        restart-keys (set (concat
                           (keys changed)
                           (->> (select-keys dependents (keys changed))
                                (mapcat val))))]

    (when (not-empty restart-keys)
      (info "reloading" (vec restart-keys))
      (swap! state update :config merge new-config)
      (apply restart restart-keys))))
