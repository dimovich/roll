(ns roll.nginx
  (:require [taoensso.timbre :refer [info]]
            [nginx.clojure.embed :as embed]
            [integrant.core :as ig]
            [roll.handler :refer [get-default-handler]]))




(defmethod ig/init-key :roll/nginx [_ {:keys [config handler] :as opts}]
  (info "starting nginx: " opts)
  (let [handler (or handler (get-default-handler))]
    (if config
      (embed/run-server config)
      (embed/run-server handler (select-keys opts [:port])))))



(defmethod ig/halt-key! :roll/nginx [_ _]
  (info "stopping nginx...")
  (embed/stop-server))
