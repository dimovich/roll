(ns roll.aleph
  (:require [taoensso.timbre :refer [info]]
            [integrant.core :as ig]
            [aleph.http :as http]
            [roll.handler :refer [get-default-handler]]
            [roll.util :as u]))



(defmethod ig/init-key :roll/aleph [_ opts]
  (when-let [opts (cond (map? opts) opts
                        (true? opts) {}
                        :default nil)]

    (info "starting roll/aleph:")
    (info (u/spp opts))

    (let [{:as opts :keys [handler port]
           :or {port    5000
                handler (get-default-handler)}} opts]
      (http/start-server handler {:port port}))))



(defmethod ig/halt-key! :roll/aleph [_ stop-fn]
  (when stop-fn
    (info "stopping roll/aleph...")
    (.close stop-fn)))

