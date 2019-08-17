(ns roll.httpkit
  (:require [taoensso.timbre :refer [info]]
            [integrant.core :as ig]
            [org.httpkit.server :as httpkit]
            [roll.handler :refer [get-default-handler]]
            [roll.util :as u]))



(defmethod ig/init-key :roll/httpkit [_ opts]
  (when-let [opts (cond (map? opts) (u/resolve-syms opts)
                        (true? opts) {}
                        :default nil)]

    (info "starting roll/httpkit:")
    (info (u/spp (update opts :handler #(cond-> % (fn? %) u/fn->name))))

    (let [{:as opts :keys [handler port]
           :or {port    5000
                handler (get-default-handler)}} opts]
      (httpkit/run-server handler {:port port}))))



(defmethod ig/halt-key! :roll/httpkit [_ stop-fn]
  (when stop-fn
    (info "stopping roll/httpkit...")
    (stop-fn)))
