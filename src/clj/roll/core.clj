(ns roll.core
  (:require [taoensso.timbre :as timbre :refer [info]]
            [taoensso.timbre.appenders.core :as appenders]
            [integrant.core :as ig]))



(defonce state (atom nil))


(derive :roll/httpkit :roll/server)
(derive :roll/nginx   :roll/server)
(derive :roll/aleph   :roll/server)


;; Timbre

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




(defn load-configs
  "Load Integrant configs. Either file path(s) or map(s)."
  [configs]
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




(defn start
  "Start all components or only the specified keys."
  ([ig-config]
   (ig/init ig-config))
  
  ([ig-config & ks]
   (ig/init ig-config ks)))




(defn stop
  "Stop all components or only the specified keys."
  ([roll]
   (some-> roll not-empty ig/halt!)
   (empty roll))
  
  ([roll & ks]
   (some-> roll not-empty (ig/halt! ks))
   (apply dissoc roll ks)))




(defn restart
  [{:as state :keys [roll config]} & ks]
  (-> state
      ;; stop
      (update :roll (partial apply stop) ks)
      ;; start
      (update :roll merge (apply start config ks))))



(defn halt! []
  (swap! state update :roll stop))



(defn add-shutdown-hook [state]
  (update state :shutdown-hook
          (fn [sh]
            (or sh (do (.addShutdownHook
                        (Runtime/getRuntime) (Thread. halt!))
                       true)))))



(defn init
  "Init Integrant using either file path(s) or map(s) configs."
  [configs]

  ;; init Timbre
  (timbre/set-config!
   {:level :info
    :output-fn (fn [{:keys [timestamp_ level msg_]}] (force msg_))
    :appenders (select-keys default-appenders [:println])})

  
  (let [ig-config (load-configs configs)]

    (swap! state add-shutdown-hook)
    
    ;; ensure we have Sente for :roll/reload
    (let [ig-config (cond-> ig-config
                      (:roll/reload ig-config)
                      (update-in [:roll/handler :sente]
                                 (fnil identity true)))]
      
      ;;stop current services
      (halt!) 

      ;; make sure registered component namespaces are loaded
      (ig/load-namespaces ig-config)

      ;; start Integrant
      (swap! state #(-> (assoc % :config ig-config)
                        (assoc   :roll   (start ig-config)))))))




(defn get-dependents
  "Recursively get component keys dependents."
  [deps cmpt-keys]
  (when (not-empty cmpt-keys)
    (let [deps-keys (->> (select-keys (:dependents deps) cmpt-keys)
                         (mapcat val))]
      (->> (get-dependents deps deps-keys)
           (concat deps-keys)
           (distinct)))))



(defn get-dependencies
  "Get component keys dependencies"
  [deps cmpt-keys]
  (->> cmpt-keys
       (select-keys (:dependencies deps))
       (mapcat val)
       (distinct)))



(defn reload
  "Check config files and restart changed keys and their dependencies."
  [paths]
  (let [new-config (load-configs paths)
        old-config (:config @state)
        deps (ig/dependency-graph new-config)
        
        deleted-keys (clojure.set/difference
                      (set (keys old-config))
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

    ;; stop deleted keys
    (when (not-empty deleted-keys)
      (info "halting" (vec deleted-keys))
      (swap! state update :roll (partial apply stop) deleted-keys))

    ;; restart changed keys
    (when (not-empty restart-keys)
      (info "restarting" (vec restart-keys))
      (swap! state (partial apply restart) restart-keys))))
