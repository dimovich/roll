(ns roll.sente
  (:require [taoensso.timbre :refer [info]]
            [taoensso.sente  :as sente :refer [cb-success?]]
            [taoensso.sente.packers.transit :as sente-transit]))



(def sente-fns (atom nil))


(defn send-evt [& args]
  (apply (:chsk-send! @sente-fns) args))


#_(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))


(defmulti event-msg-handler :id)

(defmethod event-msg-handler
  :default         ; Default/fallback case (no other matching handler)
  [{:as ev-msg :keys [event id]}])




(defn init-sente [& [opts]]
  (let [{:keys [chsk ch-recv send-fn state]}
        (sente/make-channel-socket-client!
         "/chsk"
         (-> {:packer (sente-transit/get-transit-packer)}
             (merge opts)))]
    {:chsk       chsk
     :ch-chsk    ch-recv
     :chsk-send! send-fn
     :chsk-state state}))





(defonce router_ (atom nil))
(defn  stop-router! [] (when-let [stop-f @router_] (stop-f)))
(defn start-router! [& [handler]]
  (stop-router!)
  (let [{:keys [ch-chsk]} (->> (init-sente)
                               (reset! sente-fns))]
    
    (->> (or handler event-msg-handler)
         (sente/start-client-chsk-router! ch-chsk)
         (reset! router_))))

