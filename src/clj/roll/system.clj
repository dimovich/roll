(ns roll.system
  (:require [roll.repl          :as repl]
            [roll.handler       :as handler]
            [integrant.core     :as ig]
            [org.httpkit.server :as httpkit]
            [taoensso.timbre    :as timbre :refer [info]]
            [taoensso.timbre.appenders.core :as appenders]
            [clojure.spec.alpha :as s]))


(defonce state (atom nil))

(derive :adapter/httpkit :adapter/ring)


(s/def ::port pos-int?)
(s/def ::handler var?)


(defmethod ig/pre-init-spec :adapter/ring [_]
  (s/keys :req-un [::port #_::handler]))
  


(defmethod ig/init-key :adapter/httpkit [_ opts]
  (info "starting httpkit: " opts)
  (httpkit/run-server (or (:handler opts)
                          handler/handler)
                      (select-keys opts [:port])))



(defmethod ig/halt-key! :adapter/httpkit [_ stop-fn]
  (when stop-fn
    (info "stopping httpkit...")
    (stop-fn)))


(defmethod ig/init-key :adapter/handler [_ opts]
  (info "getting handler:" (:handler opts))
  (resolve (:handler opts)))



(defmethod ig/init-key :adapter/sente [_ opts]
  (info "starting sente: " opts))



(defmethod ig/halt-key! :adapter/sente [_ stop-fn]
  (when stop-fn
    (info "stopping sente...")
    (stop-fn)))



(defmethod ig/init-key :repl/repl [_ opts]
  (info "starting repl:" opts)
  (repl/start opts))



(defmethod ig/halt-key! :repl/repl [_ server]
  (info "stopping repl...")
  (some-> server
          repl/stop))



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

