(ns example.client
  (:require [roll.sente :as sente]))


(defn ^:export init []
  (sente/start-router!))
