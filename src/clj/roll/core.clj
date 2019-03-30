(ns roll.core
  (:require [taoensso.timbre :as timbre :refer [info]]
            [taoensso.timbre.appenders.core :as appenders]
            [integrant.core :as ig]))



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
                             :default nil)]
    
    (.addShutdownHook (Runtime/getRuntime) (Thread. halt!))

    (ig/load-namespaces ig-config)
    (swap! state assoc :config ig-config)
    
    (->> (ig/init ig-config)
         (swap! state assoc :roll))))




(defn restart [& ks]
  (ig/halt! (:roll @state) ks)
  (->> (ig/init (:config @state) ks)
       (swap! state update :roll merge)))




;; move to deps.edn?
(defn -main [& args]
  (init "conf/config.edn"))

