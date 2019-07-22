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




(defn get-dependents
  "Recursively get dependents."
  [deps changed-keys]
  (when (not-empty changed-keys)
    (let [deps-keys (->> (select-keys (:dependents deps) changed-keys)
                         (mapcat val))]
      (->> (get-dependents deps deps-keys)
           (concat deps-keys)
           (distinct)))))


(defn get-dependencies [deps changed-keys]
  (->> changed-keys
       (select-keys (:dependencies deps))
       (mapcat val)
       (distinct)))



(defn reload
  "Check changed keys and restart dependecies."
  [paths]
  (let [new-config (load-configs paths)
        old-config (:config @state)
        deps (ig/dependency-graph new-config)
        
        missing-keys (clojure.set/difference (set (keys old-config))
                                             (set (keys new-config)))
        changed-keys (->> new-config
                          (reduce-kv
                           (fn [changed k v]
                             (cond-> changed
                               (not= v (get old-config k))
                               (conj k)))
                           []))

        ;; also add dependencies
        changed-keys (concat changed-keys
                             (get-dependencies deps changed-keys))

        ;; also add dependecies' dependents
        changed-keys (concat changed-keys
                             (get-dependents deps changed-keys))

        ;; also add dependents' dependencies
        changed-keys (concat changed-keys
                             (get-dependencies deps changed-keys))
        
        restart-keys (distinct changed-keys)]


    (swap! state assoc :config new-config)
    
    (when (not-empty missing-keys)
      (info "halting" (vec missing-keys))
      (ig/halt! (:roll @state) missing-keys))
    
    (when (not-empty restart-keys)
      (info "reloading" (vec restart-keys))
      (apply restart restart-keys))))

