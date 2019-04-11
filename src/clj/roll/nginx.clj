(ns roll.nginx
  (:require [taoensso.timbre :refer [info]]
            [nginx.clojure.embed :as embed]
            [integrant.core :as ig]
            [roll.handler :refer [get-default-handler]]
            [roll.util :as u]))




(defmethod ig/init-key :roll/nginx [_ opts]
  (when-let [opts (cond (map? opts) opts
                        (true? opts) {}
                        :default nil)]

    (info "starting nginx:")
    (info (u/spp opts))
    
    (let [{:as opts :keys [config handler]} opts
          handler (or handler (get-default-handler))]
      (if config
        (embed/run-server config)
        (embed/run-server handler (select-keys opts [:port]))))))



(defmethod ig/halt-key! :roll/nginx [_ _]
  (info "stopping nginx...")
  (embed/stop-server))
