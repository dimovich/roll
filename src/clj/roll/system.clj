(ns roll.system
  (:require [roll.repl          :as repl]
            [roll.handler       :as handler]
            [integrant.core     :as ig]
            [org.httpkit.server :as server]
            [taoensso.timbre    :as timbre :refer [info]]))


(defonce state (atom nil))


(defmethod ig/init-key :adapter/server [_ opts]
  (info "starting server: " opts)
  (server/run-server (or (resolve (:handler opts))
                         handler/handler)
                     (select-keys opts [:port])))



(defmethod ig/halt-key! :adapter/server [_ server]
  (when server
    (server)))



(defmethod ig/init-key :repl/repl [_ opts]
  (info "starting repl:" opts)
  (repl/start opts))



(defmethod ig/halt-key! :repl/repl [_ server]
  (some-> server
          repl/stop))



(defn destroy []
  (info "shutting down...")
  (some-> (:system @state)
          ig/halt!))



(defn init [{path :path}]
  (timbre/set-config!
   {:level :info
    :output-fn (fn [{:keys [timestamp_ level msg_]}]
                 (str (second (clojure.string/split (force timestamp_) #" ")) " "
                      (force msg_)))
    :appenders {:println (timbre/println-appender {:stream :auto})}})

  
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

