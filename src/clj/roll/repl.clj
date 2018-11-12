(ns roll.repl
  (:require [taoensso.timbre :refer [info]]
            [nrepl.server :as nrepl]
            [cider.piggieback :as pback]
            [integrant.core :as ig]))


(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))


(defn start [{port :port}]
  (nrepl/start-server :handler (nrepl-handler)
                      :middleware [pback/wrap-cljs-repl]
                      :port port))


(defn stop [server]
  (nrepl/stop-server server))



(defmethod ig/init-key :roll/repl [_ opts]
  (info "starting repl:" opts)
  (start opts))



(defmethod ig/halt-key! :roll/repl [_ server]
  (info "stopping repl...")
  (some-> server (stop)))
