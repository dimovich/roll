(ns roll.core
  (:require [taoensso.timbre :as timbre :refer [info]]
            [taoensso.timbre.appenders.core :as appenders]
            [integrant.core :as ig]
            [roll.util :as u]))



(defonce state (atom nil))


(derive :roll/httpkit :roll/server)
(derive :roll/nginx   :roll/server)
(derive :roll/aleph   :roll/server)


;; Timbre

(def default-appenders
  {:println (timbre/println-appender {:stream :auto})
   #?@(:clj [:spit (appenders/spit-appender {:fname  "timbre.log"})])})


(defmethod ig/init-key :roll/timbre [_ {:keys [appenders]}]
  (when appenders
    (info "timbre appenders:" appenders)
    (timbre/merge-config!
     {:appenders (->> appenders
                      (select-keys default-appenders)
                      (merge (zipmap (keys default-appenders)
                                     (repeat nil))))})))




(defn prep-config [ig-config]
  (cond-> ig-config
    #?@(:clj
        [ ;; ensure we have Sente for :roll/reload
         (:roll/reload ig-config)
         (-> (update :roll/sente (fnil identity {}))
             (update-in [:roll/handler :sente]
                        (fnil identity (ig/ref :roll/sente))))

         ;; ensure we have a handler for :roll/server
         (some #(isa? % :roll/server) (keys ig-config))
         (update :roll/handler (fnil identity {}))])))




(defn load-configs
  "Load Integrant configs. Either file path(s) or map(s)."
  [configs]
  (let [configs (cond-> configs
                  (not (sequential? configs)) vector)
        ;; merge all configs
        ig-config (reduce
                   (fn [all config]
                     (if-let [ig-config
                              (cond
                                #?@(:clj [(string? config) (ig/read-string (slurp config))])
                                (map? config) config
                                :default nil)]
                       (merge all ig-config)
                       all))
                   {}
                   configs)

        ;; prep global config
        ig-config (prep-config ig-config)]
     
    ;; make sure component namespaces are loaded
    #?(:clj (ig/load-namespaces ig-config))
     
    (ig/prep ig-config)))




(defn start
  "Start all components or only the specified keys."
  ([ig-config]
   (some->> (keys ig-config)
            (apply start ig-config)))
  
  ([ig-config & ks]
   (try
     (ig/init ig-config ks)
     (catch #?(:clj Throwable :cljs :default) t
       (info (ex-message t))
       (info (ex-message (ex-cause t)) "\n")
       ;; return at least keys that started
       (:system (ex-data t))))))




(defn stop
  "Stop all components or only the specified keys."
  ([roll]
   (some->> (keys roll) (apply stop roll)))
  
  ([roll & ks]
   (some-> roll not-empty (ig/halt! ks))
   (apply dissoc roll ks)))




(defn restart
  [{:as state :keys [roll config]} & ks]
  (-> state
      ;; stop
      (update :roll (partial apply stop) ks)
      ;; start
      (update :roll
              (fn [old-roll]
                (let [new-roll (apply start config ks)]
                  (with-meta (merge old-roll new-roll)
                    (meta new-roll)))))))



(defn halt! []
  (swap! state update :roll stop))


#?(:clj
   (defonce init-shutdown-hook
     (delay (.addShutdownHook (Runtime/getRuntime) (Thread. #'halt!)))))


(defn init
  "Init Integrant using either file path(s) or map(s) configs."
  [configs]

  ;; init Timbre
  (timbre/set-config!
   {:level :info
    :output-fn (fn [{:keys [timestamp_ level msg_ ?err]}]
                 (cond-> ""
                   ?err (str ?err " ")
                   msg_ (str (force msg_))))
    :appenders (select-keys default-appenders [:println])})


  ;; start Integrant
  (let [ig-config (load-configs configs)]
    (swap! state #(-> (update % :roll stop)
                      (assoc :config ig-config
                             :roll (start ig-config))))
    #?(:clj (force init-shutdown-hook))))




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
  "Check configs and restart changed keys and their dependencies."
  [paths & [watch-opts]]
  (when-let [new-config (load-configs paths)]
    (let [old-config (:config @state)
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
        (info "\nhalting" (vec deleted-keys))
        (swap! state update :roll (partial apply stop) deleted-keys))

      ;; restart changed keys
      (when (not-empty restart-keys)
        (info "restarting" (vec restart-keys))
        (swap! state (partial apply restart) restart-keys)))))
