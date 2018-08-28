(ns roll.httpkit
  (:require [taoensso.timbre :refer [info]]
            [integrant.core     :as ig]
            [org.httpkit.server :as httpkit]
            [roll.handler       :refer [get-default-handler]]))



(defmethod ig/init-key :roll/httpkit [_ opts]
  (when-let [opts (cond (map? opts) opts
                        (true? opts) {}
                        :default nil)]

    (info "starting httpkit: " opts)

    (let [{:as opts :keys [handler port]
           :or {port    5000
                handler (get-default-handler)}} opts]
      (httpkit/run-server handler {:port port}))))



(defmethod ig/halt-key! :roll/httpkit [_ stop-fn]
  (when stop-fn
    (info "stopping httpkit...")
    (stop-fn)))

