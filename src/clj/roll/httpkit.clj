(ns roll.httpkit
  (:require [taoensso.timbre :refer [info]]
            [integrant.core     :as ig]
            [org.httpkit.server :as httpkit]
            [roll.handler       :refer [get-default-handler]]))



(defmethod ig/init-key :adapter/httpkit [_ {:as opts :keys [handler]}]
  (info "starting httpkit: " opts)
  (let [handler (or handler (get-default-handler))]
    (httpkit/run-server handler (select-keys opts [:port]))))



(defmethod ig/halt-key! :adapter/httpkit [_ stop-fn]
  (when stop-fn
    (info "stopping httpkit...")
    (stop-fn)))

