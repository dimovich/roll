(ns roll.repl
  (:require [taoensso.timbre :refer [info]]
            [nrepl.server :as nrepl]
            [integrant.core :as ig]
            [roll.util :as u]))


(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))


(defn middleware []
  (vec
   (when (and (u/try-require 'cljs.core)
              (u/try-require 'cider.piggieback))
     [(ns-resolve 'cider.piggieback 'wrap-cljs-repl)])))



(defn start [{port :port}]
  (nrepl/start-server :handler (nrepl-handler)
                      :middleware (middleware)
                      :port port))


(defn stop [server]
  (nrepl/stop-server server))



(defmethod ig/init-key :roll/repl [_ opts]
  (info "starting roll/repl:" opts)
  (start opts))



(defmethod ig/halt-key! :roll/repl [_ server]
  (when server
    (info "stopping roll/repl...")
    (stop server)))
