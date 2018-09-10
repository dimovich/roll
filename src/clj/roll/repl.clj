(ns roll.repl
  (:require [taoensso.timbre :refer [info]]
            [nrepl.server :as nrepl]
            [cider.nrepl :refer [cider-nrepl-handler]]
            [integrant.core :as ig]))



(defn start [{port :port}]
  (nrepl/start-server :handler cider-nrepl-handler
                      :port port))


(defn stop [server]
  (nrepl/stop-server server))



(defmethod ig/init-key :roll/repl [_ opts]
  (info "starting repl:" opts)
  (start opts))



(defmethod ig/halt-key! :roll/repl [_ server]
  (info "stopping repl...")
  (some-> server (stop)))
