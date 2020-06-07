(ns ^:figwheel-hooks example.client
  (:require [roll.sente :as sente :refer [event-msg-handler]]
            [reagent.core :as r]))

;; will hold websocket response from server
(defonce resp (r/atom nil))

;; handle websocket handshake
(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[_ _ [data]] ?data]
    (reset! resp data)))


(defn app []
  [:div
   [:button
    {:on-click
     #(sente/send-msg
       [:client/click (rand-int 10)]
       {:timeout-ms 1000
        :cb (fn [cb-reply] (reset! resp cb-reply))})}
    "poke server"]
   [:p "Response from server: " (str @resp)]])



(defn ^:after-load mount-app []
  (r/render
   [app]
   (.getElementById js/document "app")))



(defn ^:export init []
  (sente/start!)
  (mount-app))
