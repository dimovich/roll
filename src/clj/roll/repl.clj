(ns roll.repl
  (:require [taoensso.timbre :refer [info]]
            [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer [cider-nrepl-handler]]
            [integrant.core :as ig]))



(defn start [{port :port}]
  (nrepl-server/start-server :handler cider-nrepl-handler
                             :port port))


(defn stop [server]
  (nrepl-server/stop-server server))



(defmethod ig/init-key :repl/repl [_ opts]
  (info "starting repl:" opts)
  (start opts))



(defmethod ig/halt-key! :repl/repl [_ server]
  (info "stopping repl...")
  (some-> server
          stop))
