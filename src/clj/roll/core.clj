(ns roll.core
  (:require [taoensso.timbre :as timbre :refer [info]]
            [taoensso.timbre.appenders.core :as appenders]
            [integrant.core :as ig]
            [roll.handler]
            [roll.httpkit]
            [roll.sente]
            [roll.nginx]
            [roll.data]
            [roll.repl]))


(defonce state (atom nil))

(derive :roll/httpkit :roll/ring)
(derive :roll/nginx   :roll/ring)


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
  (info "shutting down...")
  (some-> (:roll @state)
          ig/halt!))



(defn init [config]
  (timbre/set-config!
   {:level :info
    :output-fn (fn [{:keys [timestamp_ level msg_]}] (force msg_))
    :appenders (select-keys default-appenders [:println])})
  
  (when-let [ig-config (cond (string? config) (ig/read-string (slurp config))
                             (map? config)    config
                             nil)]
    
    (.addShutdownHook (Runtime/getRuntime) (Thread. halt!))

    (swap! state assoc :config ig-config)
    
    (->> (ig/init ig-config)
         (swap! state assoc :roll))))



;; (restart k1 k2 ...)
(defn restart [k]
  (ig/halt! (:roll @state) [k])
  (->> (ig/init (:config @state) [k])
       (swap! state update :roll merge)))




(defn -main [& args]
  (init "conf/config.edn"))

