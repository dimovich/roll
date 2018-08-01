(ns roll.httpkit
  (:require [taoensso.timbre :refer [info]]
            [integrant.core     :as ig]
            [org.httpkit.server :as httpkit]
            [roll.handler       :as handler]))



(defmethod ig/init-key :adapter/httpkit [_ opts]
  (info "starting httpkit: " opts)
  (httpkit/run-server (or (:handler opts)
                          handler/handler)
                      (select-keys opts [:port])))



(defmethod ig/halt-key! :adapter/httpkit [_ stop-fn]
  (when stop-fn
    (info "stopping httpkit...")
    (stop-fn)))

