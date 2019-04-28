(ns example.core
  (:require [roll.core :as roll]))

(defn handler [req]
  {:status 200 :body "Hello World!"})

(defn -main []
  (roll/init "config.edn"))

