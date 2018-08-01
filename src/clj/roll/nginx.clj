(ns roll.nginx
  (:require [taoensso.timbre :refer [info]]
            [nginx.clojure.embed :as embed]
            [roll.handler :as handler]
            [integrant.core :as ig]))




(defmethod ig/init-key
  :adapter/nginx [_ {:keys [config] :as opts}]
  (info "starting nginx: " opts)
  (if config
    (embed/run-server config)
    (embed/run-server (or (:handler opts)
                          handler/handler)
                      (select-keys opts [:port]))))



(defmethod ig/halt-key! :adapter/nginx [_ _]
  (info "stopping nginx...")
  (embed/stop-server))



