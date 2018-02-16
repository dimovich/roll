(ns roll.repl
  (:require [clojure.tools.nrepl.server :as nrepl-server]
            [cider.nrepl :refer [cider-nrepl-handler]]))



(defn start [{port :port}]
  (nrepl-server/start-server :handler cider-nrepl-handler
                             :port port))


(defn stop [server]
  (nrepl-server/stop-server server))
