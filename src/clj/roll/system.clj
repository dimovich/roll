(ns roll.system
  (:require [taoensso.timbre :as timbre :refer [info]]
            [taoensso.timbre.appenders.core :as appenders]
            [integrant.core :as ig]
            [clojure.spec.alpha :as s]
            [roll.filewatch :as fw]
            [roll.repl]
            [roll.handler]
            [roll.nginx]
            [roll.httpkit]
            [roll.sente]))


(defonce state (atom nil))

(derive :adapter/httpkit :adapter/ring)
(derive :adapter/nginx   :adapter/ring)


(s/def ::port pos-int?)
(s/def ::handler var?)


(defmethod ig/init-key :data/file [_ {:keys [path init watch] :as opts}]
  (let [opts (cond-> opts
               (symbol? init)  (update :init  resolve)
               (symbol? watch) (update :watch resolve))
        {:keys [init watch]} opts]

    (info "data file: " opts)
    
    (when init
      (@init path))

    (when-let [watch (if (true? watch) init watch)]
      (fw/start-watch! path path @watch)
      ;; return stop-fn
      #(fw/stop-watch! path))))



(defmethod ig/halt-key! :data/file [_ stop-fn]
  (when stop-fn
    (info "stopping data file watch...")
    (stop-fn)))



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



(defn init [{path :path}]
  (timbre/set-config!
   {:level :info
    :output-fn (fn [{:keys [timestamp_ level msg_]}] (force msg_))
    :appenders (select-keys default-appenders [:println])})

  
  (let [config (ig/read-string (slurp path))]
    (.addShutdownHook (Runtime/getRuntime) (Thread. destroy))

    (swap! state assoc :config config)
    
    (->> config
        ig/init
         (swap! state assoc :system))))



(defn restart [k]
  (ig/halt! (:system @state) [k])
  (->> (ig/init (:config @state) [k])
       (swap! state update :system merge)))

