(ns ^:figwheel-hooks example.client
  (:require [roll.sente :as sente :refer [event-msg-handler]]
            [reagent.core :as r]))



(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[_ _ [data]] ?data]
    (js/console.log "got handshake" data)))



(defn app []
  (r/with-let [resp (r/atom "")]
    [:div
     [:button
      {:on-click
       #(sente/send-msg
         [:client/click (rand-int 10)]
         {:timeout-ms 1000
          :cb (fn [cb-reply] (reset! resp cb-reply))})}
      "send"]
     [:div "Got " (str @resp)]]))



(defn ^:after-load mount-app []
  (r/render
   [app]
   (.getElementById js/document "app")))




(defn ^:export init []
  (sente/start-router!)
  (mount-app))
