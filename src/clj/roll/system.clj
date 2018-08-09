(ns roll.system
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

(derive :adapter/httpkit :adapter/ring)
(derive :adapter/nginx   :adapter/ring)


(def default-appenders
  {:println (timbre/println-appender {:stream :auto})
   :spit    (appenders/spit-appender {:fname  "timbre.log"})})



(defmethod ig/init-key :timbre/timbre [_ {:keys [appenders]}]
  (when appenders
    (info "timbre appenders:" appenders)
    (timbre/merge-config!
     {:appenders (->> appenders
                      (select-keys default-appenders)
                      (merge (zipmap (keys default-appenders)
                                     (repeat nil))))})))


(defn destroy []
  (info "shutting down...")
  (some-> (:system @state)
          ig/halt!))



(defn init [{:keys [config]}]
  (timbre/set-config!
   {:level :info
    :output-fn (fn [{:keys [timestamp_ level msg_]}] (force msg_))
    :appenders (select-keys default-appenders [:println])})

  
  (let [ig-config (ig/read-string (slurp config))]
    (.addShutdownHook (Runtime/getRuntime) (Thread. destroy))

    (swap! state assoc :config ig-config)
    
    (->> ig-config (ig/init)
         (swap! state assoc :system))))



(defn restart [k]
  (ig/halt! (:system @state) [k])
  (->> (ig/init (:config @state) [k])
       (swap! state update :system merge)))

