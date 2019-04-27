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


(defn halt! []
  (when-let [roll-state (:roll @state)]
    (ig/halt! roll-state)
    (info "shutting down...")))



(defn init [config]
  (timbre/set-config!
   {:level :info
    :output-fn (fn [{:keys [timestamp_ level msg_]}] (force msg_))
    :appenders (select-keys default-appenders [:println])})
  
  (when-let [ig-config (cond (string? config) (ig/read-string (slurp config))
                             ;; fixme
                             (map? config)    config
                             :default nil)]

    ;; ensure we have sente when reloading
    (let [ig-config (cond-> ig-config
                      (:roll/reload ig-config)
                      (update-in [:roll/handler :sente]
                                 (fnil identity true)))]
      
      (.addShutdownHook (Runtime/getRuntime) (Thread. halt!))

      (ig/load-namespaces ig-config)
      (swap! state assoc :config ig-config)

      (halt!) ;;stop current services
    
      (->> (ig/init ig-config)
           (swap! state assoc :roll)))))




(defn restart [& ks]
  (ig/halt! (:roll @state) ks)
  (->> (ig/init (:config @state) ks)
       (swap! state update :roll merge)))




;; move to deps.edn?
(defn -main [& args]
  (init "conf/config.edn"))

